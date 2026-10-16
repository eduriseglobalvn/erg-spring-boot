package com.erg.ergspringboot.content.lifecycle;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.content.lifecycle.ContentLifecycleRequests.TransitionRequest;
import com.erg.ergspringboot.content.lifecycle.ContentLifecycleResponses.LifecycleResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.content.ContentLifecycleTransition;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.lms.Question;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.model.quiz.version.QuizVersion;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.content.ContentLifecycleTransitionRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application boundary for the shared content lifecycle. It owns state changes and transition
 * trail writes; controllers and feature modules never write lifecycle_state directly.
 */
@Service
public class ContentLifecycleOperations implements ContentLifecyclePort {

    private static final Set<String> ELEVATED = Set.of("content.manage", "curriculum.manage", "content.lifecycle.manage");

    private final ContentLifecycleTransitionRepository transitions;
    private final CurriculumSubjectRepository subjects;
    private final CurriculumLevelRepository levels;
    private final CurriculumTopicRepository topics;
    private final ContentItemRepository contentItems;
    private final QuestionRepository questions;
    private final QuizRepository quizzes;
    private final QuizDraftRepository quizDrafts;
    private final QuizVersionRepository quizVersions;
    private final AuthorizationOperations authorization;

    public ContentLifecycleOperations(
            ContentLifecycleTransitionRepository transitions,
            CurriculumSubjectRepository subjects,
            CurriculumLevelRepository levels,
            CurriculumTopicRepository topics,
            ContentItemRepository contentItems,
            QuestionRepository questions,
            QuizRepository quizzes,
            QuizDraftRepository quizDrafts,
            QuizVersionRepository quizVersions,
            AuthorizationOperations authorization) {
        this.transitions = transitions;
        this.subjects = subjects;
        this.levels = levels;
        this.topics = topics;
        this.contentItems = contentItems;
        this.questions = questions;
        this.quizzes = quizzes;
        this.quizDrafts = quizDrafts;
        this.quizVersions = quizVersions;
        this.authorization = authorization;
    }

    @Override
    @Transactional(value = "postgresTransactionManager")
    public LifecycleResponse transition(
            UserAccessContext access,
            LifecycleResourceKind kind,
            String resourceId,
            TransitionRequest request) {
        requireAccess(access, resourceId);
        if (request == null) throw new BadRequestException("lifecycle transition request is required");
        LifecycleState targetState = parseState(request.targetState());
        if (kind == null) throw new BadRequestException("resourceKind is required");
        authorize(targetState, kind, access.tenantId());
        String key = required(request.idempotencyKey(), "idempotencyKey");
        ContentLifecycleTransition replay = transitions
                .findByTenantIdAndResourceKindAndResourceIdAndIdempotencyKey(
                        access.tenantId(), kind.name(), resourceId, key)
                .orElse(null);
        if (replay != null) {
            if (!replay.getToState().equals(targetState.name())
                    || !Objects.equals(replay.getExpectedVersion(), request.expectedVersion())
                    || !Objects.equals(replay.getOperationId(), blankToNull(request.operationId()))
                    || !Objects.equals(replay.getSourceVersionId(), blankToNull(request.sourceVersionId()))
                    || !Objects.equals(replay.getReason(), normalizedReason(targetState, request.reason()))) {
                throw new LifecycleTransitionException("IDEMPOTENCY_CONFLICT", "idempotency key was used for another lifecycle state");
            }
            return response(replay, true);
        }

        Loaded loaded = load(kind, resourceId, access.tenantId());
        boolean quizRollback = kind == LifecycleResourceKind.QUIZ
                && (loaded.state() == LifecycleState.PUBLISHED || loaded.state() == LifecycleState.ARCHIVED)
                && targetState == LifecycleState.DRAFT;
        LifecycleTransition decision = LifecycleTransitionPolicy.validate(
                loaded.state(), targetState, request.reason(), quizRollback);
        enforceApprovalSeparation(access.tenantId(), access.userId(), kind, resourceId, decision.to());
        if (request.expectedVersion() != null && request.expectedVersion() != loaded.version()) {
            throw new LifecycleTransitionException("STALE_WRITE", "content changed; reload before transitioning lifecycle");
        }

        String sourceVersionId = blankToNull(request.sourceVersionId());
        if (kind == LifecycleResourceKind.QUIZ && targetState == LifecycleState.PUBLISHED) {
            Quiz quiz = (Quiz) loaded.entity();
            if (blankToNull(quiz.getCurrentVersionId()) == null || !"published".equalsIgnoreCase(quiz.getStatus())) {
                throw new BadRequestException("quiz must be validated and published through the quiz publish contract");
            }
        }
        if (decision.rollback() && kind == LifecycleResourceKind.QUIZ) {
            sourceVersionId = rollbackQuiz((Quiz) loaded.entity(), access.userId(), sourceVersionId);
        }

        applyState(loaded.entity(), kind, targetState, access.userId());
        Object saved = save(kind, loaded.entity());
        long resultingVersion = version(saved, loaded.version() + 1);
        ContentLifecycleTransition event = event(access, kind, resourceId, loaded.state(), targetState,
                request, resultingVersion, sourceVersionId);
        transitions.save(event);
        return response(event, false);
    }

    /** Called by the immutable quiz publish transaction after validation and snapshot persistence. */
    @Override
    @Transactional(value = "postgresTransactionManager")
    public void recordPublishedSnapshot(String tenantId, String resourceId, String actorId, String idempotencyKey) {
        Quiz quiz = quizzes.findByIdAndTenantId(resourceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz not found"));
        String key = blankToNull(idempotencyKey);
        if (key == null) key = "quiz-publish:" + resourceId + ":" + quiz.getCurrentVersionId();
        ContentLifecycleTransition existing = transitions
                .findByTenantIdAndResourceKindAndResourceIdAndIdempotencyKey(
                        tenantId, LifecycleResourceKind.QUIZ.name(), resourceId, key)
                .orElse(null);
        if (existing != null) return;
        LifecycleState previous = state(quiz.getLifecycleState(), quiz.getStatus());
        if (previous == LifecycleState.PUBLISHED) return;
        LifecycleTransitionPolicy.validateValidatedQuizPublish(previous,
                "validated immutable quiz publish");
        quiz.setLifecycleState(LifecycleState.PUBLISHED.name());
        quiz.setStatus("published");
        quizzes.save(quiz);
        ContentLifecycleTransition event = new ContentLifecycleTransition();
        event.setTenantId(tenantId);
        event.setResourceKind(LifecycleResourceKind.QUIZ.name());
        event.setResourceId(resourceId);
        event.setFromState(previous.name());
        event.setToState(LifecycleState.PUBLISHED.name());
        event.setActorId(required(actorId, "actorId"));
        event.setReason("validated immutable quiz publish");
        event.setResultingVersion(version(quiz, 1));
        event.setIdempotencyKey(key);
        transitions.save(event);
    }

    private void requireAccess(UserAccessContext access, String resourceId) {
        if (access == null || blankToNull(access.tenantId()) == null || blankToNull(access.userId()) == null) {
            throw new ForbiddenException("authenticated tenant context is required");
        }
        if (blankToNull(resourceId) == null) throw new BadRequestException("resourceId is required");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ErgPrincipal)) {
            throw new ForbiddenException("authenticated principal is required");
        }
        ErgPrincipal principal = (ErgPrincipal) authentication.getPrincipal();
        if (!access.tenantId().equals(principal.tenantId())
                || !access.userId().equals(principal.userId())) {
            throw new ForbiddenException("access context does not match the authenticated principal");
        }
    }

    private Loaded load(LifecycleResourceKind kind, String id, String tenantId) {
        Object entity = switch (kind) {
            case CURRICULUM_SUBJECT -> subjects.findByTenantIdAndId(tenantId, id)
                    .orElseThrow(() -> notFound(kind, id));
            case CURRICULUM_LEVEL -> levels.findByTenantIdAndId(tenantId, id)
                    .orElseThrow(() -> notFound(kind, id));
            case CURRICULUM_TOPIC -> topics.findByTenantIdAndId(tenantId, id)
                    .orElseThrow(() -> notFound(kind, id));
            case CONTENT_ITEM -> contentItems.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> notFound(kind, id));
            case QUESTION -> questions.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> notFound(kind, id));
            case QUIZ -> quizzes.findByIdAndTenantId(id, tenantId)
                    .orElseThrow(() -> notFound(kind, id));
        };
        return new Loaded(entity, state(stateValue(entity), statusValue(entity)), version(entity, 0));
    }

    private RuntimeException notFound(LifecycleResourceKind kind, String id) {
        return new ResourceNotFoundException(kind.name().toLowerCase(Locale.ROOT) + " not found: " + id);
    }

    private void authorize(LifecycleState target, LifecycleResourceKind kind, String tenantId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ErgPrincipal principal)
                || principal.tenantId() == null || principal.tenantId().isBlank()) {
            throw new ForbiddenException("authenticated principal is required");
        }
        String required = switch (target) {
            case IN_REVIEW -> "content.lifecycle.submit";
            case APPROVED -> "content.lifecycle.approve";
            case PUBLISHED -> kind == LifecycleResourceKind.QUIZ ? "lms.quiz.publish" : "content.lifecycle.publish";
            case ARCHIVED -> "content.lifecycle.archive";
            case DRAFT -> "content.lifecycle.rollback";
        };
        if (!hasPermission(principal, required, kind, tenantId)) {
            throw new ForbiddenException("missing permission " + required);
        }
    }

    private boolean hasPermission(ErgPrincipal principal, String required, LifecycleResourceKind kind, String tenantId) {
        if (authorization == null) {
            Set<String> permissions = principal.permissions() == null ? Set.of() : Set.copyOf(principal.permissions());
            return permissions.stream().map(this::normalize).anyMatch(required::equals);
        }
        if (required.equals("lms.quiz.publish") && kind == LifecycleResourceKind.QUIZ) {
            return authorization.isAllowed(principal, required, "teacher", tenantId)
                    || authorization.isAllowed(principal, "content.lifecycle.publish", "admin", tenantId)
                    || authorization.isAllowed(principal, "content.manage", "admin", tenantId);
        }
        return authorization.isAllowed(principal, required, "admin", tenantId)
                || authorization.isAllowed(principal, "content.manage", "admin", tenantId)
                || authorization.isAllowed(principal, "curriculum.manage", "admin", tenantId);
    }

    private void enforceApprovalSeparation(String tenantId, String actorId, LifecycleResourceKind kind, String resourceId, LifecycleState target) {
        if (target != LifecycleState.APPROVED || hasElevatedPermission()) return;
        transitions.findTopByTenantIdAndResourceKindAndResourceIdAndToStateOrderByCreatedAtDesc(
                        tenantId, kind.name(), resourceId, LifecycleState.IN_REVIEW.name())
                .filter(last -> actorId.equals(last.getActorId()))
                .ifPresent(last -> { throw new ForbiddenException("review submitter cannot approve without elevated permission"); });
    }

    private boolean hasElevatedPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ErgPrincipal principal)) return false;
        if (authorization == null) {
            return principal.permissions().stream().map(this::normalize).anyMatch(ELEVATED::contains);
        }
        return authorization.isAllowedAny(principal, "admin", principal.tenantId(),
                List.of("content.manage", "curriculum.manage", "content.lifecycle.manage"));
    }

    private void applyState(Object entity, LifecycleResourceKind kind, LifecycleState target, String actorId) {
        String status = statusFor(kind, target);
        switch (kind) {
            case CURRICULUM_SUBJECT -> { CurriculumSubject value = (CurriculumSubject) entity; value.setLifecycleState(target.name()); value.setStatus(status); value.setUpdatedBy(actorId); }
            case CURRICULUM_LEVEL -> { CurriculumLevel value = (CurriculumLevel) entity; value.setLifecycleState(target.name()); value.setStatus(status); value.setUpdatedBy(actorId); }
            case CURRICULUM_TOPIC -> { CurriculumTopic value = (CurriculumTopic) entity; value.setLifecycleState(target.name()); value.setStatus(status); value.setUpdatedBy(actorId); }
            case CONTENT_ITEM -> { ContentItem value = (ContentItem) entity; value.setLifecycleState(target.name()); value.setStatus(status); value.setUpdatedBy(actorId); }
            case QUESTION -> {
                Question value = (Question) entity;
                value.setLifecycleState(target.name());
                value.setStatus(status);
                value.setArchivedAt(target == LifecycleState.ARCHIVED
                        ? OffsetDateTime.now(ZoneOffset.UTC) : null);
            }
            case QUIZ -> { Quiz value = (Quiz) entity; value.setLifecycleState(target.name()); value.setStatus(status); }
        }
    }

    private Object save(LifecycleResourceKind kind, Object entity) {
        return switch (kind) {
            case CURRICULUM_SUBJECT -> subjects.saveAndFlush((CurriculumSubject) entity);
            case CURRICULUM_LEVEL -> levels.saveAndFlush((CurriculumLevel) entity);
            case CURRICULUM_TOPIC -> topics.saveAndFlush((CurriculumTopic) entity);
            case CONTENT_ITEM -> contentItems.saveAndFlush((ContentItem) entity);
            case QUESTION -> questions.saveAndFlush((Question) entity);
            case QUIZ -> quizzes.saveAndFlush((Quiz) entity);
        };
    }

    private String rollbackQuiz(Quiz quiz, String actorId, String sourceVersionId) {
        String versionId = blankToNull(sourceVersionId);
        if (versionId == null) versionId = blankToNull(quiz.getCurrentVersionId());
        if (versionId == null) throw new BadRequestException("quiz rollback requires a published version");
        QuizVersion version = quizVersions.findByIdAndTenantId(versionId, quiz.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("quiz version not found"));
        if (!quiz.getId().equals(version.getQuizId())) throw new BadRequestException("source version does not belong to quiz");
        Map<String, Object> snapshot = version.getAuthoringSnapshot() == null ? Map.of() : version.getAuthoringSnapshot();
        QuizDraft draft = new QuizDraft();
        draft.setQuizId(quiz.getId());
        draft.setTenantId(quiz.getTenantId());
        draft.setSettings(asMap(snapshot.get("settings")));
        draft.setPlayerTemplate(asMap(snapshot.get("playerTemplate")));
        draft.setTree(snapshot.get("tree"));
        draft.setSchemaVersion(1);
        draft.setDocVersion(0L);
        draft.setBaseVersionId(version.getId());
        draft.setUpdatedBy(actorId);
        QuizDraft saved = quizDrafts.save(draft);
        quiz.setLatestDraftId(saved.getId());
        return version.getId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private ContentLifecycleTransition event(UserAccessContext access, LifecycleResourceKind kind, String id,
                                             LifecycleState from, LifecycleState to, TransitionRequest request,
                                             long resultingVersion, String sourceVersionId) {
        ContentLifecycleTransition event = new ContentLifecycleTransition();
        event.setTenantId(access.tenantId());
        event.setResourceKind(kind.name());
        event.setResourceId(id);
        event.setFromState(from.name());
        event.setToState(to.name());
        event.setActorId(access.userId());
        event.setReason(normalizedReason(to, request.reason()));
        event.setExpectedVersion(request.expectedVersion());
        event.setResultingVersion(resultingVersion);
        event.setOperationId(blankToNull(request.operationId()));
        event.setIdempotencyKey(required(request.idempotencyKey(), "idempotencyKey"));
        event.setSourceVersionId(blankToNull(sourceVersionId));
        return event;
    }

    private LifecycleResponse response(ContentLifecycleTransition event, boolean replayed) {
        return new LifecycleResponse(
                LifecycleResourceKind.valueOf(event.getResourceKind()),
                event.getResourceId(), event.getTenantId(),
                LifecycleState.valueOf(event.getFromState()), LifecycleState.valueOf(event.getToState()),
                event.getResultingVersion() == null ? 0 : event.getResultingVersion(), event.getActorId(), event.getReason(),
                replayed, event.getSourceVersionId(), event.getCreatedAt());
    }

    private LifecycleState parseState(String value) {
        try { return LifecycleState.parse(value); }
        catch (IllegalArgumentException exception) { throw new BadRequestException(exception.getMessage()); }
    }

    private String defaultReason(LifecycleState target) {
        return switch (target) {
            case IN_REVIEW -> "submitted for review";
            case APPROVED -> "approved by workflow";
            default -> "lifecycle transition";
        };
    }

    private String normalizedReason(LifecycleState target, String reason) {
        return blankToNull(reason) == null ? defaultReason(target) : reason.trim();
    }

    private LifecycleState state(String lifecycleState, String status) {
        if (blankToNull(lifecycleState) != null
                && !("DRAFT".equalsIgnoreCase(lifecycleState)
                && status != null && !"draft".equalsIgnoreCase(status))) {
            try { return LifecycleState.parse(lifecycleState); } catch (IllegalArgumentException ignored) { }
        }
        String normalized = status == null ? "draft" : status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "published", "active" -> LifecycleState.PUBLISHED;
            case "approved" -> LifecycleState.APPROVED;
            case "in_review", "review" -> LifecycleState.IN_REVIEW;
            case "archived" -> LifecycleState.ARCHIVED;
            default -> LifecycleState.DRAFT;
        };
    }

    private String stateValue(Object entity) {
        return switch (entity) {
            case CurriculumSubject value -> value.getLifecycleState();
            case CurriculumLevel value -> value.getLifecycleState();
            case CurriculumTopic value -> value.getLifecycleState();
            case ContentItem value -> value.getLifecycleState();
            case Question value -> value.getLifecycleState();
            case Quiz value -> value.getLifecycleState();
            default -> null;
        };
    }

    private String statusValue(Object entity) {
        return switch (entity) {
            case CurriculumSubject value -> value.getStatus();
            case CurriculumLevel value -> value.getStatus();
            case CurriculumTopic value -> value.getStatus();
            case ContentItem value -> value.getStatus();
            case Question value -> value.getStatus();
            case Quiz value -> value.getStatus();
            default -> null;
        };
    }

    private long version(Object entity, long fallback) {
        Long value = switch (entity) {
            case CurriculumSubject target -> target.getRecordVersion();
            case CurriculumLevel target -> target.getRecordVersion();
            case CurriculumTopic target -> target.getRecordVersion();
            case ContentItem target -> target.getRecordVersion();
            case Question target -> target.getRecordVersion();
            case Quiz target -> target.getRecordVersion();
            default -> null;
        };
        return value == null ? fallback : value;
    }

    private String statusFor(LifecycleResourceKind kind, LifecycleState state) {
        if (state == LifecycleState.PUBLISHED && (kind == LifecycleResourceKind.CURRICULUM_SUBJECT
                || kind == LifecycleResourceKind.CURRICULUM_LEVEL || kind == LifecycleResourceKind.CURRICULUM_TOPIC)) return "active";
        return state.name().toLowerCase(Locale.ROOT);
    }

    private String required(String value, String name) {
        String normalized = blankToNull(value);
        if (normalized == null) throw new BadRequestException(name + " is required");
        return normalized;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private record Loaded(Object entity, LifecycleState state, long version) { }
}
