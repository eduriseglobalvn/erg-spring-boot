package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy.AssignedQuiz;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.model.lms.*;
import com.erg.ergspringboot.model.lms.AttemptAnswer;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.service.lms.*;
import com.erg.ergspringboot.service.quiz.runtime.*;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.utils.IdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

/** Transactional attempt command boundary. It owns idempotency, attempt rules and grading orchestration. */
@Service
public class LmsAttemptUseCase {
    private static final Logger log = LoggerFactory.getLogger(LmsAttemptUseCase.class);
    private final LmsAssignmentAccessPolicy policy;
    private final LmsQuizService quizzes;
    private final LmsAttemptService attempts;
    private final LmsStudentService students;
    private final QuizRuntimePackageService packages;
    private final QuizAttemptGradingService grading;
    private final AttemptEventRecorder events;
    private final LmsReadCacheInvalidator caches;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Duration submitGrace;
    private final LmsSyncOperationService sync;

    @Autowired
    public LmsAttemptUseCase(LmsAssignmentAccessPolicy policy, LmsQuizService quizzes, LmsAttemptService attempts,
            LmsStudentService students, QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, LmsReadCacheInvalidator caches, ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<LmsSyncOperationService> sync,
            @Value("${erg.lms.attempt.submit-grace-seconds:90}") long submitGraceSeconds) {
        this(policy, quizzes, attempts, students, packages, grading, events, caches,
                meterRegistry.getIfAvailable(), sync.getIfAvailable(), Clock.systemUTC(),
                Duration.ofSeconds(Math.max(0, submitGraceSeconds)));
    }

    /** Explicit wiring (deterministic clock / grace) for contract tests and replay tooling. */
    public LmsAttemptUseCase(LmsAssignmentAccessPolicy policy, LmsQuizService quizzes, LmsAttemptService attempts,
            LmsStudentService students, QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, LmsReadCacheInvalidator caches, MeterRegistry meterRegistry,
            LmsSyncOperationService sync, Clock clock, Duration submitGrace) {
        this.policy = policy; this.quizzes = quizzes; this.attempts = attempts; this.students = students;
        this.packages = packages; this.grading = grading; this.events = events; this.caches = caches;
        this.meterRegistry = meterRegistry;
        this.sync = sync;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.submitGrace = submitGrace == null ? Duration.ZERO : submitGrace;
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse start(String studentId, StartAttemptRequest request, String idempotencyKey,
            UserAccessContext access) {
        String tenant = tenant(access.tenantId());
        Assignment assignment = policy.assignment(tenant, request.assignmentId());
        AssignedQuiz assignedQuiz = policy.assignedQuiz(assignment, request.quizId())
                .orElseThrow(() -> new BadRequestException("assignment does not include quiz"));
        Student learner = policy.learner(tenant, studentId).orElse(null);
        // Recipient membership (not the primary class of a multi-class assignment) authorizes a learner.
        if (!policy.isRecipient(assignment, learner, studentId)) throw new BadRequestException("student is not assigned");
        if (!LmsStatePolicy.assignmentIsVisible(assignment.getStatus())) throw new BadRequestException("assignment is not open");
        String persistedStudent = learner == null ? studentId : learner.getId();
        OffsetDateTime now = now();
        String key = blank(idempotencyKey);
        if (key != null) {
            Optional<Attempt> existing = attempts.findByStartIdempotencyKey(tenant, request.assignmentId(), request.quizId(), persistedStudent, key);
            if (existing.isPresent()) return response(existing.get());
        }
        Optional<Attempt> inProgress = attempts.findInProgress(tenant, request.assignmentId(), request.quizId(), persistedStudent);
        LmsSyncOperationService.Receipt receipt = reserve(tenant, persistedStudent, key, "quiz.attempt.start",
                request.assignmentId() + ":" + request.quizId() + ":" + persistedStudent, request, null);
        if (receipt != null && receipt.replayed()) return sync.result(receipt, AttemptResponse.class);
        if (inProgress.isPresent() && isExpired(inProgress.get(), assignment, now)) {
            autoClose(inProgress.get(), assignment, tenant, now);
            inProgress = Optional.empty();
        }
        if (inProgress.isPresent()) {
            Attempt attempt = inProgress.get();
            ensureResumePackageMatches(attempt, request);
            attempt.setLastActiveAt(now);
            Attempt saved = attempts.save(attempt);
            log.info("EXAM_ATTEMPT_RESUME studentId={} attemptId={} quizId={} assignmentId={}", persistedStudent, saved.getId(), request.quizId(), request.assignmentId());
            caches.evictAfterCommit(tenant, LmsReadCacheInvalidator.ATTEMPT_READ_CACHES);
            AttemptResponse result = response(saved);
            complete(receipt, result, version(saved));
            return result;
        }

        ensureOpen(assignment, now);
        Optional<QuizPackage> runtimePackage = assignedQuiz.quizVersionId() == null
                ? packages.activePackageEntity(tenant, request.quizId())
                : packages.packageEntityForVersion(tenant, request.quizId(), assignedQuiz.quizVersionId());
        Quiz quiz = quizzes.findByTenantIdAndId(tenant, request.quizId()).orElse(null);
        validatePackageContract(request, runtimePackage);
        ensureLimit(assignment, tenant, request.quizId(), persistedStudent);
        Attempt attempt = newAttempt(tenant, persistedStudent, request, assignment, quiz, runtimePackage, now, key);
        attempt.setClassId(blank(assignment.getAcademicClassId()) == null && learner != null
                ? learner.getAcademicClassId() : assignment.getAcademicClassId());
        attempt.setLastActiveAt(now);
        Attempt saved;
        try {
            saved = attempts.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException raced) {
            // uq_lms_attempts_single_in_progress: a concurrent start already opened the attempt.
            throw new ConflictException("an attempt is already in progress for this quiz");
        }
        log.info("EXAM_ATTEMPT_START studentId={} attemptId={} quizId={} assignmentId={} mode={}", persistedStudent, saved.getId(), request.quizId(), request.assignmentId(), saved.getMode());
        caches.evictAfterCommit(tenant, LmsReadCacheInvalidator.ATTEMPT_READ_CACHES);
        AttemptResponse result = response(saved);
        complete(receipt, result, version(saved));
        return result;
    }

    /**
     * Legacy compatibility endpoint. Learner drafts stay in browser storage until submit, so this
     * deliberately validates access without writing an attempt, event, sync receipt, or cache.
     */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AttemptResponse saveDraft(String id, AttemptDraftRequest request, UserAccessContext access) {
        log.info("EXAM_ATTEMPT_DRAFT studentId={} attemptId={}", access.userId(), id);
        Attempt attempt = mutable(id, access).attempt();
        editable(attempt);
        return response(attempt);
    }

    /** Legacy compatibility endpoint. Answers are accepted only in the final submit payload. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AnswerResultResponse saveAnswer(String id, String questionId, SaveAnswerRequest request, UserAccessContext access) {
        if (questionId.contains(".") || questionId.contains("$")) throw new BadRequestException("invalid question id");
        Attempt attempt = mutable(id, access).attempt();
        editable(attempt);
        return new AnswerResultResponse(id, questionId, false);
    }

    /** Legacy compatibility endpoint. Offline state remains client-side until final submit. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AttemptSyncResponse sync(String id, AttemptSyncRequest request, UserAccessContext access) {
        Attempt attempt = mutable(id, access).attempt();
        if (request != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            return new AttemptSyncResponse("conflict", response(attempt), List.of("package_hash"));
        }
        return new AttemptSyncResponse(attempt.getStatus(), response(attempt), List.of());
    }

    @Transactional("postgresTransactionManager")
    public AttemptSubmitResponse submit(String id, SubmitAttemptRequest request, UserAccessContext access) {
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        String tenant = tenant(access.tenantId());
        Owned owned = mutable(id, access);
        Attempt attempt = owned.attempt();
        if (request != null && request.packageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) throw new BadRequestException("package_hash conflict");
        String submitKey = blank(request == null ? null : request.idempotencyKey());
        LmsSyncOperationService.Receipt receipt = reserve(tenant, access.userId(), submitKey,
                "quiz.attempt.submit", id, request, version(attempt));
        if (receipt != null && receipt.replayed()) return sync.result(receipt, AttemptSubmitResponse.class);
        if (submitKey != null && submitKey.equals(blank(attempt.getSubmitIdempotencyKey()))) {
            complete(receipt, submitResult(attempt), version(attempt));
            return submitResult(attempt);
        }
        if (!LmsStatePolicy.attemptIsEditable(attempt.getStatus())) {
            AttemptSubmitResponse result = submitResult(attempt);
            complete(receipt, result, version(attempt));
            return result;
        }
        if (submitKey != null && blank(attempt.getSubmitIdempotencyKey()) != null) {
            throw new ConflictException("attempt submission is already in progress");
        }
        Map<String, AttemptAnswer> answers = new LinkedHashMap<>(map(attempt.getAnswers()));
        if (request != null && request.answers() != null) request.answers().forEach((qid, answer) -> answers.put(qid, answer(qid, answer, Map.of(), now())));
        // submittedAt is accepted for wire compatibility only; expiry and audit authority are server time.
        OffsetDateTime submitted = now();
        boolean late = ensureSubmitAllowed(attempt, owned.assignment(), submitted);
        if (submitKey != null) attempt.setSubmitIdempotencyKey(submitKey);
        Score score = finish(attempt, answers, tenant, submitted, late);
        Attempt saved = attempts.save(attempt);
        log.info("EXAM_ATTEMPT_SUBMIT studentId={} attemptId={} quizId={} assignmentId={} score={}/{} percent={}% passed={} late={}", saved.getStudentId(), saved.getId(), saved.getQuizId(), saved.getAssignmentId(), score.score(), score.max(), score.percent(), score.passed(), late);
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_submitted", null, null, Map.of("score", score.score(), "percent", score.percent(), "passed", score.passed(), "late", late), submitted);
        if (request != null) events.recordClientBatch(saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), request.clientEvents());
        projectAfterCommit(saved, owned.assignment(), tenant);
        caches.evictAfterCommit(tenant, LmsReadCacheInvalidator.ATTEMPT_READ_CACHES);
        if (sample != null) sample.stop(meterRegistry.timer("erg.lms.attempt.submit", "result", score.passed() ? "passed" : "failed"));
        AttemptSubmitResponse result = new AttemptSubmitResponse(score.score(), score.max(), score.percent(), score.passed());
        complete(receipt, result, version(saved));
        return result;
    }

    /**
     * Teacher grade for one answer awaiting manual review. Recomputes totals from the normalized answer rows and
     * moves the attempt to {@code graded} once no manual answer is left; the attempt {@code @Version} plus the
     * optional {@code expectedVersion} reject concurrent graders with 409.
     */
    @Transactional("postgresTransactionManager")
    public AttemptResponse gradeManualAnswer(String attemptId, String questionId, ManualGradeRequest request,
            UserAccessContext access) {
        String tenant = tenant(access.tenantId());
        Attempt attempt = attempts.findByTenantIdAndId(tenant, attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("attempt not found"));
        Assignment assignment = policy.assignment(tenant, attempt.getAssignmentId());
        if (!policy.isReviewer()) throw new ForbiddenException("Grading requires a teacher grading permission");
        policy.ensureReviewScope(assignment, attempt.getStudentId(), access);
        if (request == null || request.points() == null) throw new BadRequestException("points is required");
        if (request.expectedVersion() != null && request.expectedVersion() != version(attempt)) {
            throw new ConflictException("Attempt has been updated by another request.");
        }
        if (!LmsStatePolicy.attemptNeedsReview(attempt.getStatus())) throw new ConflictException("attempt is not awaiting manual grading");
        if (blank(attempt.getQuizVersionId()) == null) throw new BadRequestException("attempt has no published version to grade");
        var result = grading.gradeManualAnswer(tenant, attempt.getId(), attempt.getQuizVersionId(), questionId,
                BigDecimal.valueOf(request.points()), access.userId());
        attempt.setScore(result.score().doubleValue());
        attempt.setMaxScore(result.maxScore().doubleValue());
        attempt.setPercent(result.percent());
        attempt.setPassed(result.passed());
        attempt.setStatus(result.needsManualGrade() ? LmsStatePolicy.manualReviewAttemptState() : LmsStatePolicy.gradedAttemptState());
        attempt.setUpdatedAt(now());
        Attempt saved = attempts.save(attempt);
        log.info("EXAM_ATTEMPT_GRADED attemptId={} questionId={} graderId={} status={}", saved.getId(), questionId, access.userId(), saved.getStatus());
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_answer_graded", questionId, null, Map.of("score", result.score().doubleValue(), "status", saved.getStatus()), now());
        projectAfterCommit(saved, assignment, tenant);
        caches.evictAfterCommit(tenant, LmsReadCacheInvalidator.ATTEMPT_READ_CACHES);
        return response(saved);
    }

    private record Owned(Attempt attempt, Assignment assignment) { }
    private record Score(double score, double max, double percent, boolean passed) { }

    private Owned mutable(String id, UserAccessContext access) {
        Attempt attempt = attempts.findByTenantIdAndId(tenant(access.tenantId()), id).orElseThrow(() -> new ResourceNotFoundException("attempt not found"));
        Assignment assignment = policy.assignment(access.tenantId(), attempt.getAssignmentId());
        policy.ensureAttemptAccess(attempt, assignment, access);
        return new Owned(attempt, assignment);
    }

    /**
     * Grades and closes the attempt. Answers are filtered to the version's (or legacy quiz's) real question ids.
     * Without a gradable published version the attempt is never auto-scored: it waits for review with score 0.
     */
    private Score finish(Attempt attempt, Map<String, AttemptAnswer> submittedAnswers, String tenant,
            OffsetDateTime submitted, boolean late) {
        Set<String> questionIds = grading.runtimeQuestionIds(attempt.getQuizVersionId());
        boolean versioned = !questionIds.isEmpty();
        if (!versioned) {
            questionIds = new LinkedHashSet<>(quizzes.findByTenantIdAndId(tenant, attempt.getQuizId())
                    .map(Quiz::getQuestionIds).map(this::list).orElse(List.of()));
        }
        Map<String, AttemptAnswer> answers = new LinkedHashMap<>();
        Set<String> allowed = questionIds;
        submittedAnswers.forEach((qid, value) -> { if (allowed.contains(qid)) answers.put(qid, value); });
        attempt.setAnswers(answers); attempt.setSubmittedAt(submitted); attempt.setUpdatedAt(now());
        attempt.setLastActiveAt(attempt.getUpdatedAt()); attempt.setLate(late);
        Optional<QuizAttemptGradingService.GradedAttempt> graded = versioned
                ? grading.gradeSubmission(tenant, attempt.getId(), attempt.getQuizVersionId(), raw(answers))
                : Optional.empty();
        Score score;
        if (graded.isPresent()) {
            var result = graded.get();
            score = new Score(result.score().doubleValue(), result.maxScore().doubleValue(), result.percent(), result.passed());
            attempt.setStatus(result.needsManualGrade() ? LmsStatePolicy.manualReviewAttemptState() : LmsStatePolicy.submittedAttemptState());
            attempt.setTotalQuestions(result.totalQuestions());
        } else {
            log.warn("EXAM_ATTEMPT_UNGRADED attemptId={} quizId={} reason=no_published_version", attempt.getId(), attempt.getQuizId());
            score = new Score(0, Math.max(0, questionIds.size()), 0, false);
            attempt.setStatus(LmsStatePolicy.manualReviewAttemptState());
            attempt.setTotalQuestions(questionIds.size());
        }
        attempt.setScore(score.score()); attempt.setMaxScore(score.max()); attempt.setPercent(score.percent());
        attempt.setPassed(score.passed()); attempt.setSubmittedCount(answers.size());
        return score;
    }

    /** Closes an in-progress attempt whose deadline plus grace passed, grading whatever the server holds. */
    private void autoClose(Attempt attempt, Assignment assignment, String tenant, OffsetDateTime now) {
        Score score = finish(attempt, new LinkedHashMap<>(map(attempt.getAnswers())), tenant, now, true);
        Attempt saved = attempts.save(attempt);
        log.info("EXAM_ATTEMPT_AUTO_CLOSED attemptId={} studentId={} score={}", saved.getId(), saved.getStudentId(), score.score());
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_auto_closed", null, null, Map.of("score", score.score(), "late", true), now);
        projectAfterCommit(saved, assignment, tenant);
    }

    private Attempt newAttempt(String tenant, String student, StartAttemptRequest request, Assignment assignment,
            Quiz quiz, Optional<QuizPackage> activePackage, OffsetDateTime now, String key) {
        Attempt attempt = new Attempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(tenant);
        attempt.setAssignmentId(request.assignmentId());
        attempt.setQuizId(request.quizId());
        attempt.setStudentId(student);
        QuizPackage pkg = activePackage.orElse(null);
        String packageId = pkg == null ? request.packageId() : pkg.getId();
        String packageHash = pkg == null ? request.packageHash() : canonicalPackageHash(pkg, request.packageHash());
        String versionId = pkg == null ? (quiz == null ? null : quiz.getCurrentVersionId()) : pkg.getQuizVersionId();
        String gradingMode = pkg == null ? null : pkg.getGradingMode();
        int packageQuestionCount = pkg == null ? 0 : totalQuestions(pkg.getPackageJson());
        int questionCount = Math.max(packageQuestionCount, questionCount(quiz));
        attempt.setPackageId(packageId);
        attempt.setPackageHash(packageHash);
        attempt.setQuizVersionId(versionId);
        attempt.setQuizVersion(pkg == null ? null : pkg.getQuizVersion());
        attempt.setGradingMode(gradingMode);
        attempt.setStartIdempotencyKey(key);
        attempt.setMode(mode(quiz == null ? null : quiz.getKind()));
        attempt.setStatus(LmsStatePolicy.initialAttemptState());
        attempt.setSubmittedCount(0);
        attempt.setTotalQuestions(questionCount);
        attempt.setLastActiveAt(now);
        attempt.setAnswers(new LinkedHashMap<>());
        attempt.setEvents(List.of());
        attempt.setClient(Map.of());
        attempt.setScore(0.0);
        attempt.setMaxScore(100.0);
        attempt.setPercent(0.0);
        attempt.setPassed(false);
        attempt.setStartedAt(now);
        attempt.setUpdatedAt(now);
        attempt.setPolicySnapshot(policySnapshot(request, assignment, pkg, versionId, packageId, packageHash,
                gradingMode, questionCount, now));
        return attempt;
    }

    private void validatePackageContract(StartAttemptRequest request, Optional<QuizPackage> activePackage) {
        if (activePackage.isEmpty()) {
            return; // Legacy quizzes without a published lineage use the request fields as a compatibility bridge.
        }
        QuizPackage pkg = activePackage.get();
        String expectedHash = canonicalPackageHash(pkg, null);
        if (!Objects.equals(pkg.getId(), request.packageId()) || !Objects.equals(expectedHash, request.packageHash())) {
            throw new BadRequestException("package contract mismatch");
        }
    }

    private void ensureResumePackageMatches(Attempt attempt, StartAttemptRequest request) {
        if (attempt.getPackageId() != null && !Objects.equals(attempt.getPackageId(), request.packageId())) {
            throw new BadRequestException("attempt package contract mismatch");
        }
        if (attempt.getPackageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            throw new BadRequestException("attempt package contract mismatch");
        }
    }

    private String canonicalPackageHash(QuizPackage pkg, String fallback) {
        String hash = blank(pkg.getContentHash());
        return hash == null ? blank(fallback) : hash;
    }

    private Map<String, Object> policySnapshot(StartAttemptRequest request, Assignment assignment, QuizPackage pkg,
            String versionId, String packageId, String packageHash, String gradingMode, int questionCount,
            OffsetDateTime startedAt) {
        OffsetDateTime deadline = deadline(assignment, startedAt);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignmentId", request.assignmentId());
        snapshot.put("quizId", request.quizId());
        snapshot.put("quizVersionId", versionId);
        snapshot.put("quizVersion", pkg == null ? null : pkg.getQuizVersion());
        snapshot.put("packageId", packageId);
        snapshot.put("packageHash", packageHash);
        snapshot.put("teacherId", assignment.getAssignedBy());
        snapshot.put("questionCount", questionCount);
        snapshot.put("durationMinutes", assignment.getMaxDurationMinutes());
        snapshot.put("deadlineAt", deadline == null ? null : deadline.toString());
        snapshot.put("submitGraceSeconds", submitGrace.toSeconds());
        snapshot.put("attemptLimit", assignment.getAttemptLimit());
        snapshot.put("gradingMode", gradingMode);
        snapshot.put("offlineAllowed", assignment.isOfflineAllowed()
                && "server-authoritative".equalsIgnoreCase(gradingMode));
        snapshot.put("startedAt", startedAt.toString());
        snapshot.put("serverTimeAtStart", startedAt.toString());
        snapshot.put("sourceAssignmentUpdatedAt", assignment.getUpdatedAt() == null ? null : assignment.getUpdatedAt().toString());
        return snapshot;
    }

    private OffsetDateTime deadline(Assignment assignment, OffsetDateTime startedAt) {
        OffsetDateTime result = assignment.getDueAt();
        Integer minutes = assignment.getMaxDurationMinutes();
        if (minutes != null && minutes > 0 && startedAt != null) {
            OffsetDateTime durationDeadline = startedAt.plusMinutes(minutes);
            result = result == null || durationDeadline.isBefore(result) ? durationDeadline : result;
        }
        return result;
    }

    /** Deadline frozen in the start snapshot; legacy attempts without one fall back to the live assignment. */
    private OffsetDateTime effectiveDeadline(Attempt attempt, Assignment assignment) {
        OffsetDateTime snapshot = snapshotDeadline(attempt);
        return snapshot != null ? snapshot : deadline(assignment, attempt.getStartedAt());
    }

    private boolean isExpired(Attempt attempt, Assignment assignment, OffsetDateTime now) {
        OffsetDateTime deadline = effectiveDeadline(attempt, assignment);
        return deadline != null && now.isAfter(deadline.plus(submitGrace));
    }

    private int questionCount(Quiz quiz) {
        return quiz == null || quiz.getQuestionIds() == null ? 0 : quiz.getQuestionIds().size();
    }
    private void ensureOpen(Assignment a, OffsetDateTime now) { if (a.getStartAt() != null && now.isBefore(a.getStartAt())) throw new BadRequestException("assignment has not started"); if (a.getDueAt() != null && now.isAfter(a.getDueAt())) throw new BadRequestException("assignment is past due"); }
    private void ensureLimit(Assignment a, String tenant, String quiz, String student) { Integer limit = a.getAttemptLimit(); if (limit != null && limit > 0 && attempts.countCompletedForLimit(tenant, a.getId(), quiz, student) >= limit) throw new ConflictException("attempt limit reached"); }

    /** Accepts submissions up to {@code deadline + grace}; returns true when the submission is late. */
    private boolean ensureSubmitAllowed(Attempt attempt, Assignment assignment, OffsetDateTime submitted) {
        OffsetDateTime deadline = effectiveDeadline(attempt, assignment);
        if (deadline == null || !submitted.isAfter(deadline)) return false;
        if (!submitted.isAfter(deadline.plus(submitGrace))) return true;
        Integer minutes = snapshotDeadline(attempt) != null ? snapshotInteger(attempt, "durationMinutes") : assignment.getMaxDurationMinutes();
        if (minutes != null && minutes > 0 && attempt.getStartedAt() != null
                && submitted.isAfter(attempt.getStartedAt().plusMinutes(minutes).plus(submitGrace))) {
            throw new BadRequestException("attempt duration exceeded");
        }
        throw new BadRequestException("assignment is past due");
    }
    private void editable(Attempt a) { if (!LmsStatePolicy.attemptIsEditable(a.getStatus())) throw new BadRequestException("attempt already submitted"); }
    private int totalQuestions(Map<String,Object> json) { Object q = json == null ? null : json.get("quiz"); if (!(q instanceof Map<?,?> qm)) return 0; Object sections = ((Map<?,?>) qm).get("sections"); if (!(sections instanceof List<?> list)) return 0; return list.stream().filter(Map.class::isInstance).mapToInt(x -> { Object qs = ((Map<?,?>) x).get("questions"); return qs instanceof List<?> l ? l.size() : 0; }).sum(); }
    private String mode(String kind) { return "test".equalsIgnoreCase(kind) || "testing".equalsIgnoreCase(kind) ? "testing" : "training"; }
    private Map<String,Object> raw(Map<String,AttemptAnswer> answers) { Map<String,Object> out = new LinkedHashMap<>(); answers.forEach((k,v) -> out.put(k, v == null ? null : v.getAnswer())); return out; }
    private AttemptAnswer answer(String id, Object value, Map<String,Object> client, OffsetDateTime at) { return new AttemptAnswer(id, value, client, at); }
    private AttemptSubmitResponse submitResult(Attempt attempt) {
        return new AttemptSubmitResponse(value(attempt.getScore()), value(attempt.getMaxScore()),
                value(attempt.getPercent()), Boolean.TRUE.equals(attempt.getPassed()));
    }

    private OffsetDateTime snapshotDeadline(Attempt attempt) {
        Object value = map(attempt.getPolicySnapshot()).get("deadlineAt");
        if (value == null || value.toString().isBlank()) return null;
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer snapshotInteger(Attempt attempt, String key) {
        Object value = map(attempt.getPolicySnapshot()).get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null || value.toString().isBlank()) return null;
        try { return Integer.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; }
    }

    private AttemptResponse response(Attempt a) {
        Map<String,com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> out = new LinkedHashMap<>();
        map(a.getAnswers()).forEach((k,v) -> out.put(k, new com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer(
                v == null ? null : v.getAnswer(), v == null ? Map.of() : map(v.getClientResult()),
                v == null ? null : v.getAnsweredAt())));
        return new AttemptResponse(a.getId(), a.getAssignmentId(), a.getQuizId(), a.getStudentId(), a.getPackageId(),
                a.getPackageHash(), a.getStatus(), out, value(a.getScore()), value(a.getMaxScore()), value(a.getPercent()),
                Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt(), a.getUpdatedAt(),
                AttemptPolicySnapshotResponse.from(a.getPolicySnapshot()));
    }

    /**
     * The student_progress read projection is derived data: it is written after the attempt transaction
     * committed (in its own transaction), so a projection failure can never roll back a graded submission.
     */
    private void projectAfterCommit(Attempt a, Assignment assignment, String tenant) {
        String classId = assignment == null || blank(assignment.getAcademicClassId()) == null ? a.getClassId() : assignment.getAcademicClassId();
        Student student = students.findByTenantIdAndId(tenant, a.getStudentId())
                .or(() -> students.findFirstByTenantIdAndAuthUserId(tenant, a.getStudentId())).orElse(null);
        String studentName = student == null ? null : student.getFullName();
        String progressId = student == null || student.getAuthUserId() == null ? a.getStudentId() : student.getAuthUserId();
        String title = quizzes.findByTenantIdAndId(tenant, a.getQuizId()).map(Quiz::getTitle).orElse(null);
        int answered = a.getSubmittedCount() == null ? 0 : a.getSubmittedCount();
        int total = a.getTotalQuestions() == null ? 0 : a.getTotalQuestions();
        BigDecimal score = BigDecimal.valueOf(value(a.getScore()));
        boolean passed = Boolean.TRUE.equals(a.getPassed());
        String status = a.getStatus();
        Runnable projection = () -> {
            try {
                grading.projectProgress(tenant, progressId, studentName, a.getQuizId(), title, a.getAssignmentId(),
                        classId, a.getMode(), status, answered, total, score, passed, a.getStartedAt(), a.getSubmittedAt());
            } catch (RuntimeException ex) {
                // The attempt is already committed; the projection is rebuilt on the next attempt write.
                log.warn("Failed to project student_progress for attempt {}: {}", a.getId(), ex.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            projection.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { projection.run(); }
        });
    }
    private LmsSyncOperationService.Receipt reserve(String tenant, String account, String key, String type,
                                                     String entityId, Object payload, Long expectedVersion) {
        if (sync == null || key == null || key.isBlank()) return null;
        return sync.reserve(tenant, account, key, key, type, entityId, payload, expectedVersion).orElse(null);
    }
    private void complete(LmsSyncOperationService.Receipt receipt, Object result, long version) {
        if (receipt != null) sync.complete(receipt, result, version);
    }
    private long version(Attempt attempt) { return attempt.getRecordVersion() == null ? 0L : attempt.getRecordVersion(); }
    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private double value(Double value) { return value == null ? 0 : value; }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
