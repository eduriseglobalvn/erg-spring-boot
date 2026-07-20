package com.erg.ergspringboot.service.quiz.authoring;

import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.ReorderRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SaveDraftRequest;
import com.erg.ergspringboot.common.request.quiz.QuizAuthoringRequests.SlideUpsertRequest;
import com.erg.ergspringboot.common.response.quiz.QuizAuthoringResponses.SaveDraftResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.exception.StaleDraftException;
import com.erg.ergspringboot.model.quiz.authoring.QuizDraft;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.service.quiz.authoring.QuizQuestionLinkService.QuestionLinkInput;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft autosave with optimistic locking (spec §4.2, §8). Every write must present the current
 * {@code docVersion}; a mismatch is rejected as {@link StaleDraftException} (409 STALE_DRAFT) so
 * concurrent editors reload/merge instead of silently clobbering. On success {@code docVersion}
 * increments and is echoed back.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class QuizDraftService {

    private final QuizDraftRepository quizDraftRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionLinkService quizQuestionLinkService;

    public QuizDraftService(
            QuizDraftRepository quizDraftRepository,
            QuizRepository quizRepository,
            QuizQuestionLinkService quizQuestionLinkService
    ) {
        this.quizDraftRepository = quizDraftRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionLinkService = quizQuestionLinkService;
    }

    /** Full-document autosave (PUT). Replaces settings/playerTemplate/tree wholesale. */
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace", "quizRuntimePackage"}, allEntries = true)
    public SaveDraftResponse saveDraft(String tenantId, String quizId, SaveDraftRequest request) {
        QuizDraft draft = latestDraft(tenantId, quizId);
        assertDocVersion(draft, request == null ? null : request.docVersion());
        if (request.settings() != null) {
            draft.setSettings(request.settings());
        }
        if (request.playerTemplate() != null) {
            draft.setPlayerTemplate(request.playerTemplate());
        }
        if (request.tree() != null) {
            draft.setTree(request.tree());
            syncQuizQuestionIds(tenantId, quizId, request.tree());
        }
        return bump(draft);
    }

    /** Upserts a single slide into a group tree (§4.2). Server assigns the slide id when absent. */
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace", "quizRuntimePackage"}, allEntries = true)
    @SuppressWarnings("unchecked")
    public SaveDraftResponse upsertSlide(String tenantId, String quizId, SlideUpsertRequest request) {
        QuizDraft draft = latestDraft(tenantId, quizId);
        assertDocVersion(draft, request == null ? null : request.docVersion());
        if (request.groupId() == null || request.groupId().isBlank()) {
            throw new BadRequestException("groupId is required");
        }
        if (request.slide() == null) {
            throw new BadRequestException("slide is required");
        }
        // Slide-tree mutation stays whole-document (spec §1.2): the tree blob is the source of truth.
        List<Map<String, Object>> groups = asGroupList(draft.getTree());
        Map<String, Object> group = findOrCreateGroup(groups, request.groupId());
        List<Map<String, Object>> slides = (List<Map<String, Object>>) group.computeIfAbsent(
                "slides", key -> new java.util.ArrayList<Map<String, Object>>());
        Map<String, Object> slide = new java.util.LinkedHashMap<>(request.slide());
        slide.putIfAbsent("slideId", com.erg.ergspringboot.utils.IdGenerator.newId());
        replaceOrAppend(slides, slide, request.afterSlideId());
        draft.setTree(groups);
        syncQuizQuestionIds(tenantId, quizId, groups);
        return bump(draft);
    }

    /** Reorders groups/slides (§4.2). */
    @CacheEvict(cacheNames = {"lcmsQuestionBankWorkspace", "lcmsQuizWorkspace", "quizRuntimePackage"}, allEntries = true)
    public SaveDraftResponse reorder(String tenantId, String quizId, ReorderRequest request) {
        QuizDraft draft = latestDraft(tenantId, quizId);
        assertDocVersion(draft, request == null ? null : request.docVersion());
        if (request.groups() == null) {
            throw new BadRequestException("groups is required");
        }
        List<Map<String, Object>> groups = asGroupList(draft.getTree());
        for (ReorderRequest.ReorderGroup ordering : request.groups()) {
            Map<String, Object> group = findGroup(groups, ordering.groupId());
            if (group != null) {
                group.put("slides", reorderSlides(asSlideList(group.get("slides")), ordering.slideIds()));
            }
        }
        draft.setTree(groups);
        syncQuizQuestionIds(tenantId, quizId, groups);
        return bump(draft);
    }

    private void syncQuizQuestionIds(String tenantId, String quizId, Object tree) {
        List<String> questionIds = quizQuestionLinkService.syncManualQuestionLinks(tenantId, quizId, extractQuestionLinks(tree));
        quizRepository.findByIdAndTenantId(quizId, tenantId).ifPresent(quiz -> {
            quiz.setQuestionIds(questionIds);
            quizRepository.save(quiz);
        });
    }

    private List<QuestionLinkInput> extractQuestionLinks(Object tree) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        List<QuestionLinkInput> result = new java.util.ArrayList<>();
        for (Map<String, Object> group : asGroupList(tree)) {
            for (Map<String, Object> slide : asSlideList(group.get("slides"))) {
                Object questionId = slide.get("questionId");
                if (questionId instanceof String value && !value.isBlank()) {
                    String normalized = value.trim();
                    if (ids.add(normalized)) {
                        result.add(new QuestionLinkInput(normalized, questionSetSettings(slide), null));
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> questionSetSettings(Map<String, Object> slide) {
        Object settings = firstNonNull(
                slide.get("questionSetSettings"),
                slide.get("settingsOverride"),
                slide.get("overrideSettings")
        );
        if (settings instanceof Map<?, ?> map) {
            return new java.util.LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private SaveDraftResponse bump(QuizDraft draft) {
        draft.setDocVersion(draft.getDocVersion() + 1);
        QuizDraft saved = quizDraftRepository.save(draft);
        return new SaveDraftResponse(saved.getDocVersion());
    }

    private QuizDraft latestDraft(String tenantId, String quizId) {
        return quizDraftRepository.findFirstByQuizIdAndTenantIdOrderByUpdatedAtDesc(quizId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("quiz draft not found"));
    }

    /**
     * Optimistic-lock guard for draft writes (spec §4.2, save-on-exit model).
     *
     * <p>The editor holds the whole document client-side and only persists on exit (or an occasional
     * checkpoint), so {@code docVersion} is OPTIONAL. When the client omits it (null) this is a
     * last-write-wins save: it always applies over the server's current state. When the client DOES send
     * a {@code docVersion} (e.g. a second editor open on the same quiz wants collision protection) a
     * mismatch is still rejected as {@link StaleDraftException} (409 STALE_DRAFT). This keeps the common
     * single-editor path frictionless while preserving conflict detection for anyone who opts in.
     */
    private void assertDocVersion(QuizDraft draft, Long requestedDocVersion) {
        if (requestedDocVersion == null) {
            return;
        }
        if (!requestedDocVersion.equals(draft.getDocVersion())) {
            throw new StaleDraftException(draft.getDocVersion());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asGroupList(Object tree) {
        if (tree instanceof List<?> list) {
            List<Map<String, Object>> groups = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    groups.add(new java.util.LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return groups;
        }
        return new java.util.ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asSlideList(Object slides) {
        if (slides instanceof List<?> list) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    private Map<String, Object> findGroup(List<Map<String, Object>> groups, String groupId) {
        return groups.stream()
                .filter(group -> groupId != null && groupId.equals(group.get("groupId")))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> findOrCreateGroup(List<Map<String, Object>> groups, String groupId) {
        Map<String, Object> existing = findGroup(groups, groupId);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> group = new java.util.LinkedHashMap<>();
        group.put("groupId", groupId);
        group.put("slides", new java.util.ArrayList<Map<String, Object>>());
        groups.add(group);
        return group;
    }

    private void replaceOrAppend(List<Map<String, Object>> slides, Map<String, Object> slide, String afterSlideId) {
        Object slideId = slide.get("slideId");
        for (int index = 0; index < slides.size(); index++) {
            if (slideId != null && slideId.equals(slides.get(index).get("slideId"))) {
                slides.set(index, slide);
                return;
            }
        }
        int insertAt = slides.size();
        if (afterSlideId != null) {
            for (int index = 0; index < slides.size(); index++) {
                if (afterSlideId.equals(slides.get(index).get("slideId"))) {
                    insertAt = index + 1;
                    break;
                }
            }
        }
        slides.add(insertAt, slide);
    }

    private List<Map<String, Object>> reorderSlides(List<Map<String, Object>> slides, List<String> slideIds) {
        if (slideIds == null || slideIds.isEmpty()) {
            return slides;
        }
        List<Map<String, Object>> ordered = new java.util.ArrayList<>();
        for (String slideId : slideIds) {
            slides.stream()
                    .filter(slide -> slideId != null && slideId.equals(slide.get("slideId")))
                    .findFirst()
                    .ifPresent(ordered::add);
        }
        // Keep any slides not named in the ordering, appended in their existing order.
        for (Map<String, Object> slide : slides) {
            if (!ordered.contains(slide)) {
                ordered.add(slide);
            }
        }
        return ordered;
    }
}
