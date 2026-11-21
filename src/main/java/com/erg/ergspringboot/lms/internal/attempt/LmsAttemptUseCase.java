package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.internal.core.LmsQuizService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy;
import com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy.AssignedQuiz;
import com.erg.ergspringboot.lms.LmsReadCacheInvalidator;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.QuizStartInfo;
import com.erg.ergspringboot.lms.persistence.*;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.*;
import com.erg.ergspringboot.lms.persistence.AttemptAnswer;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.lms.internal.quiz.runtime.*;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional attempt command boundary. It owns idempotency, attempt rules and grading orchestration. */
@Service
public class LmsAttemptUseCase {
    private static final int MAX_DRAFT_ANSWERS = 500; // bounds one draft write; a quiz has far fewer questions
    private static final Logger log = LoggerFactory.getLogger(LmsAttemptUseCase.class);
    private final LmsAssignmentAccessPolicy policy;
    private final LmsQuizService quizzes;
    private final LmsAttemptService attempts;
    private final QuizRuntimePackageService packages;
    private final QuizAttemptGradingService grading;
    private final AttemptEventRecorder events;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final LmsSyncOperationService sync;
    private final AttemptDeadlinePolicy deadlines;
    private final AttemptFactory factory;
    private final AttemptProgressProjector progress;
    private final AttemptCompletionEffects effects;
    private TransactionTemplate submitTx; // null in explicit wiring: submit then runs in the caller's transaction

    @Autowired
    public LmsAttemptUseCase(LmsAssignmentAccessPolicy policy, LmsQuizService quizzes, LmsAttemptService attempts,
            LmsStudentService students, QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, LmsReadCacheInvalidator caches, ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<LmsSyncOperationService> sync, ApplicationEventPublisher publisher,
            @Value("${erg.lms.attempt.submit-grace-seconds:90}") long submitGraceSeconds) {
        this(policy, quizzes, attempts, students, packages, grading, events, caches,
                meterRegistry.getIfAvailable(), sync.getIfAvailable(), publisher, Clock.systemUTC(),
                Duration.ofSeconds(Math.max(0, submitGraceSeconds)));
    }

    /** Explicit wiring (deterministic clock / grace) for contract tests and replay tooling. */
    public LmsAttemptUseCase(LmsAssignmentAccessPolicy policy, LmsQuizService quizzes, LmsAttemptService attempts,
            LmsStudentService students, QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, LmsReadCacheInvalidator caches, MeterRegistry meterRegistry,
            LmsSyncOperationService sync, ApplicationEventPublisher publisher, Clock clock, Duration submitGrace) {
        this.policy = policy; this.quizzes = quizzes; this.attempts = attempts;
        this.packages = packages; this.grading = grading; this.events = events;
        this.meterRegistry = meterRegistry;
        this.sync = sync;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.deadlines = new AttemptDeadlinePolicy(submitGrace);
        this.factory = new AttemptFactory(deadlines);
        this.progress = new AttemptProgressProjector(policy.identities(), students, packages, grading);
        this.effects = new AttemptCompletionEffects(events, progress, caches, publisher);
    }

    /** Required: without it submit would silently run with no transaction (only hand-wired test fixtures skip it). */
    @Autowired
    public void setTransactionManager(@Qualifier("postgresTransactionManager") PlatformTransactionManager manager) {
        this.submitTx = new TransactionTemplate(manager);
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
        attempts.lockStart(tenant, request.assignmentId(), request.quizId(), persistedStudent);
        if (key != null) {
            Optional<Attempt> existing = attempts.findByStartIdempotencyKey(tenant, request.assignmentId(), request.quizId(), persistedStudent, key);
            if (existing.isPresent()) return AttemptResponses.of(existing.get());
        }
        Optional<Attempt> inProgress = attempts.findInProgress(tenant, request.assignmentId(), request.quizId(), persistedStudent);
        LmsSyncOperationService.Receipt receipt = reserve(tenant, persistedStudent, key, "quiz.attempt.start",
                request.assignmentId() + ":" + request.quizId() + ":" + persistedStudent, request, null);
        if (receipt != null && receipt.replayed()) return sync.result(receipt, AttemptResponse.class);
        if (inProgress.isPresent() && deadlines.isExpired(inProgress.get(), assignment, now)) {
            Attempt closed = autoClose(inProgress.get(), assignment, tenant, now);
            if (deadlines.isPastDue(assignment, now)) {
                // Return the closed attempt instead of throwing: an exception would roll the auto-close back.
                effects.evictReads(tenant, closed, assignment, studentId);
                AttemptResponse result = AttemptResponses.of(closed);
                complete(receipt, result, version(closed));
                return result;
            }
            inProgress = Optional.empty();
        }
        if (inProgress.isPresent()) {
            Attempt attempt = inProgress.get();
            factory.ensureResumePackageMatches(attempt, request);
            attempt.setLastActiveAt(now);
            Attempt saved = attempts.save(attempt);
            log.info("EXAM_ATTEMPT_RESUME studentId={} attemptId={} quizId={} assignmentId={}", persistedStudent, saved.getId(), request.quizId(), request.assignmentId());
            effects.evictReads(tenant, saved, assignment, studentId);
            AttemptResponse result = AttemptResponses.of(saved);
            complete(receipt, result, version(saved));
            return result;
        }

        deadlines.ensureOpen(assignment, now);
        // Cached projection: the package_json and the quiz jsonb columns are not loaded per start.
        QuizStartInfo info = packages.startInfo(tenant, request.quizId(), assignedQuiz.quizVersionId());
        factory.validatePackageContract(request, info);
        ensureLimit(assignment, tenant, request.quizId(), persistedStudent);
        Attempt attempt = factory.newAttempt(tenant, persistedStudent, request, assignment, info, now, key);
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
        effects.evictReads(tenant, saved, assignment, studentId); // studentId is the caller's login user id
        AttemptResponse result = AttemptResponses.of(saved);
        complete(receipt, result, version(saved));
        return result;
    }

    /** Keeps answers so far on the open attempt for resume (start returns them); submit merges and grades them. */
    @Transactional("postgresTransactionManager")
    public AttemptResponse saveDraft(String id, AttemptDraftRequest request, UserAccessContext access) {
        Attempt attempt = ownAttempt(id, access);
        editable(attempt);
        if (request == null || request.answers() == null || request.answers().isEmpty()) return AttemptResponses.of(attempt);
        boolean otherPackage = request.packageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash());
        if (otherPackage) throw new BadRequestException("package_hash conflict");
        if (request.answers().size() > MAX_DRAFT_ANSWERS) throw new BadRequestException("too many answers in draft");
        OffsetDateTime now = now();
        Map<String, AttemptAnswer> answers = new LinkedHashMap<>(map(attempt.getAnswers()));
        request.answers().forEach((questionId, value) -> {
            if (questionId == null || questionId.isBlank() || questionId.matches(".*[.$].*")) throw new BadRequestException("invalid question id");
            if (value == null) answers.remove(questionId); else answers.put(questionId, answer(questionId, value, Map.of(), now));
        });
        attempt.setAnswers(answers);
        attempt.setLastActiveAt(now);
        return AttemptResponses.of(attempts.save(attempt));
    }

    /** Legacy compatibility endpoint. Answers are accepted only in the final submit payload. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AnswerResultResponse saveAnswer(String id, String questionId, SaveAnswerRequest request, UserAccessContext access) {
        if (questionId.contains(".") || questionId.contains("$")) throw new BadRequestException("invalid question id");
        Attempt attempt = mutable(id, access, false).attempt();
        editable(attempt);
        return new AnswerResultResponse(id, questionId, false);
    }

    /** Legacy compatibility endpoint. Offline state remains client-side until final submit. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AttemptSyncResponse sync(String id, AttemptSyncRequest request, UserAccessContext access) {
        Attempt attempt = mutable(id, access, false).attempt();
        if (request != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            return new AttemptSyncResponse("conflict", AttemptResponses.of(attempt), List.of("package_hash"));
        }
        return new AttemptSyncResponse(attempt.getStatus(), AttemptResponses.of(attempt), List.of());
    }

    /** Autosave can win the {@code @Version} race with the hand-in: retry once in a fresh transaction. */
    public AttemptSubmitResponse submit(String id, SubmitAttemptRequest request, UserAccessContext access) {
        if (submitTx == null) return submitOnce(id, request, access);
        try {
            return submitTx.execute(status -> submitOnce(id, request, access));
        } catch (ObjectOptimisticLockingFailureException raced) {
            return submitTx.execute(status -> submitOnce(id, request, access));
        }
    }

    private AttemptSubmitResponse submitOnce(String id, SubmitAttemptRequest request, UserAccessContext access) {
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        String tenant = tenant(access.tenantId());
        Owned owned = mutable(id, access, true); // submit always reads the assignment fresh: deadlines decide late/closed
        Attempt attempt = owned.attempt();
        if (request != null && request.packageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) throw new BadRequestException("package_hash conflict");
        String submitKey = blank(request == null ? null : request.idempotencyKey());
        LmsSyncOperationService.Receipt receipt = reserve(tenant, access.userId(), submitKey,
                "quiz.attempt.submit", id, request, version(attempt));
        if (receipt != null && receipt.replayed()) return sync.result(receipt, AttemptSubmitResponse.class);
        if (submitKey != null && submitKey.equals(blank(attempt.getSubmitIdempotencyKey()))) {
            complete(receipt, AttemptResponses.submit(attempt), version(attempt));
            return AttemptResponses.submit(attempt);
        }
        if (!LmsStatePolicy.attemptIsEditable(attempt.getStatus())) {
            AttemptSubmitResponse result = AttemptResponses.submit(attempt);
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
        // Past deadline + grace the payload answers are still graded and stored as a late hand-in, never dropped.
        boolean late = deadlines.isLate(attempt, owned.assignment(), submitted);
        if (submitKey != null) attempt.setSubmitIdempotencyKey(submitKey);
        Score score = finish(attempt, answers, tenant, submitted, late);
        Attempt saved = attempts.save(attempt);
        log.info("EXAM_ATTEMPT_SUBMIT studentId={} attemptId={} quizId={} assignmentId={} score={}/{} percent={}% passed={} late={}", saved.getStudentId(), saved.getId(), saved.getQuizId(), saved.getAssignmentId(), score.score(), score.max(), score.percent(), score.passed(), late);
        effects.submitted(saved, owned.assignment(), tenant, score, submitted, late, access.userId());
        if (request != null) events.recordClientBatch(saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), request.clientEvents());
        if (sample != null) sample.stop(meterRegistry.timer("erg.lms.attempt.submit", "result", score.passed() ? "passed" : "failed"));
        AttemptSubmitResponse result = new AttemptSubmitResponse(score.score(), score.max(), score.percent(), score.passed());
        complete(receipt, result, version(saved));
        return result;
    }

    /** Offline import: builds, server-grades and stores the finished attempt (server clock is authoritative). */
    Attempt saveImported(String tenant, Student student, QuizPackage pkg, Assignment assignment, String key,
            Map<String, Object> clientMeta, Map<String, Object> answers) {
        OffsetDateTime now = now();
        Attempt attempt = factory.newAttempt(tenant, student.getId(), new StartAttemptRequest(assignment.getId(), pkg.getQuizId(),
                pkg.getId(), pkg.getContentHash()), assignment,
                QuizStartInfo.of(quizzes.findByTenantIdAndId(tenant, pkg.getQuizId()).orElse(null), pkg), now, key);
        attempt.setClassId(blank(assignment.getAcademicClassId()) == null ? student.getAcademicClassId() : assignment.getAcademicClassId());
        attempt.setSubmitIdempotencyKey(key); attempt.setClient(clientMeta);
        Map<String, AttemptAnswer> stored = new LinkedHashMap<>(); answers.forEach((qid, value) -> stored.put(qid, answer(qid, value, Map.of(), now)));
        boolean late = deadlines.effectiveDeadline(attempt, assignment) instanceof OffsetDateTime deadline && now.isAfter(deadline);
        Score score = finish(attempt, stored, tenant, now, late); Attempt saved = attempts.saveAndFlush(attempt);
        effects.submitted(saved, assignment, tenant, score, now, late); return saved;
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
        // An older attempt must not overwrite student_progress written by the learner's newer hand-in.
        boolean latest = attempts.findLatestSubmitted(tenant, saved.getAssignmentId(), saved.getStudentId())
                .map(newest -> newest.getId().equals(saved.getId())).orElse(true);
        if (latest) progress.project(saved, assignment, tenant);
        effects.evictReads(tenant, saved, assignment);
        return AttemptResponses.of(saved);
    }

    private record Owned(Attempt attempt, Assignment assignment) { }
    record Score(double score, double max, double percent, boolean passed) { }

    /**
     * The caller's own attempt, or a reviewer's. The assignment is loaded only when the caller needs it (submit deadlines,
     * the reviewer scope): autosave and sync never use it, so the owner path skips that query.
     */
    /** Draft autosave belongs to the learner: a reviewer who may read or grade the attempt must not overwrite its answers. */
    private Attempt ownAttempt(String id, UserAccessContext access) {
        Attempt attempt = attempts.findByTenantIdAndId(tenant(access.tenantId()), id).orElseThrow(() -> new ResourceNotFoundException("attempt not found"));
        if (!policy.isOwner(attempt, access)) throw new ForbiddenException("You can only save answers on your own attempt");
        return attempt;
    }

    private Owned mutable(String id, UserAccessContext access, boolean withAssignment) {
        Attempt attempt = attempts.findByTenantIdAndId(tenant(access.tenantId()), id).orElseThrow(() -> new ResourceNotFoundException("attempt not found"));
        if (!withAssignment && policy.isOwner(attempt, access)) return new Owned(attempt, null);
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

    /** Sweep entry (P2-5): closes the attempt when its deadline plus grace passed; true when it was closed. */
    @Transactional("postgresTransactionManager")
    public boolean closeIfExpired(String tenant, String attemptId, OffsetDateTime now) {
        Attempt attempt = attempts.findByTenantIdAndId(tenant, attemptId).orElse(null);
        if (attempt == null || !LmsStatePolicy.initialAttemptState().equals(LmsStatePolicy.normalizeState(attempt.getStatus()))) {
            return false;
        }
        Assignment assignment = policy.assignment(tenant, attempt.getAssignmentId());
        if (!deadlines.isExpired(attempt, assignment, now)) return false;
        autoClose(attempt, assignment, tenant, now);
        effects.evictReads(tenant, attempt, assignment);
        return true;
    }

    /** Closes an in-progress attempt whose deadline plus grace passed, grading whatever the server holds. */
    private Attempt autoClose(Attempt attempt, Assignment assignment, String tenant, OffsetDateTime now) {
        Score score = finish(attempt, new LinkedHashMap<>(map(attempt.getAnswers())), tenant, now, true);
        Attempt saved = attempts.save(attempt);
        log.info("EXAM_ATTEMPT_AUTO_CLOSED attemptId={} studentId={} score={}", saved.getId(), saved.getStudentId(), score.score());
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_auto_closed", null, null, Map.of("score", score.score(), "late", true), now);
        progress.project(saved, assignment, tenant);
        return saved;
    }

    private void ensureLimit(Assignment a, String tenant, String quiz, String student) { Integer limit = a.getAttemptLimit(); if (limit != null && limit > 0 && attempts.countCompletedForLimit(tenant, a.getId(), quiz, student) >= limit) throw new ConflictException("attempt limit reached"); }
    private void editable(Attempt a) { if (!LmsStatePolicy.attemptIsEditable(a.getStatus())) throw new BadRequestException("attempt already submitted"); }
    private Map<String,Object> raw(Map<String,AttemptAnswer> answers) { Map<String,Object> out = new LinkedHashMap<>(); answers.forEach((k,v) -> out.put(k, v == null ? null : v.getAnswer())); return out; }
    private AttemptAnswer answer(String id, Object value, Map<String,Object> client, OffsetDateTime at) { return new AttemptAnswer(id, value, client, at); }
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
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
