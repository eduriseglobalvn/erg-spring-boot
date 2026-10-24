package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.model.lms.*;
import com.erg.ergspringboot.model.quiz.version.QuizPackage;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.service.lms.*;
import com.erg.ergspringboot.service.quiz.runtime.*;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.utils.IdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

/** Transactional attempt command boundary. It owns idempotency, attempt rules and grading orchestration. */
@Service
public class LmsAttemptUseCase {
    private static final Logger log = LoggerFactory.getLogger(LmsAttemptUseCase.class);
    private static final List<String> READ_CACHES = List.of("lmsHomeworkWorkspace", "lmsClassWorkspace",
            "lmsAssignmentProgress", "studentDashboard", "quizAssignmentReport", "quizQuestionAnalytics");
    private final LmsClassroomService classrooms;
    private final LmsAssignmentService assignments;
    private final LmsQuizService quizzes;
    private final LmsAttemptService attempts;
    private final LmsStudentService students;
    private final QuizRuntimePackageService packages;
    private final QuizAttemptGradingService grading;
    private final AttemptEventRecorder events;
    private final CacheManager caches;
    private final Optional<MeterRegistry> meterRegistry;
    private final Clock clock;
    private final LmsSyncOperationService sync;

    /** Compatibility constructor retained for legacy callers/tests wiring without metrics. */
    public LmsAttemptUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsQuizService quizzes, LmsAttemptService attempts, LmsStudentService students,
            QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, CacheManager caches) {
        this(classrooms, assignments, quizzes, attempts, students, packages, grading, events,
                caches, Optional.empty(), Clock.systemUTC(), null);
    }

    /** Deterministic-clock constructor used by contract tests and replay tooling. */
    public LmsAttemptUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsQuizService quizzes, LmsAttemptService attempts, LmsStudentService students,
            QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, CacheManager caches, Clock clock) {
        this(classrooms, assignments, quizzes, attempts, students, packages, grading, events,
                caches, Optional.empty(), clock, null);
    }

    @Autowired
    public LmsAttemptUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsQuizService quizzes, LmsAttemptService attempts, LmsStudentService students,
            QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, CacheManager caches, ObjectProvider<MeterRegistry> meterRegistry,
            ObjectProvider<LmsSyncOperationService> sync) {
        this(classrooms, assignments, quizzes, attempts, students, packages, grading, events,
                caches, Optional.ofNullable(meterRegistry.getIfAvailable()), Clock.systemUTC(),
                sync == null ? null : sync.getIfAvailable());
    }

    private LmsAttemptUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsQuizService quizzes, LmsAttemptService attempts, LmsStudentService students,
            QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, CacheManager caches, Optional<MeterRegistry> meterRegistry, Clock clock,
            LmsSyncOperationService sync) {
        this.classrooms = classrooms; this.assignments = assignments; this.quizzes = quizzes;
        this.attempts = attempts; this.students = students; this.packages = packages;
        this.grading = grading; this.events = events; this.caches = caches;
        this.meterRegistry = meterRegistry;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.sync = sync;
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse start(String studentId, StartAttemptRequest request, String idempotencyKey,
            UserAccessContext access) {
        String tenant = tenant(access.tenantId());
        Assignment assignment = assignment(request.assignmentId(), tenant);
        ensureClassAccess(assignment.getAcademicClassId(), access);
        if (!Objects.equals(assignment.getQuizId(), request.quizId())) throw new BadRequestException("assignment does not include quiz");
        if (!assigned(assignment, studentId)) throw new BadRequestException("student is not assigned");
        String persistedStudent = students.findFirstByTenantIdAndAuthUserId(tenant, studentId).map(Student::getId).orElse(studentId);
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
        if (inProgress.isPresent()) {
            Attempt attempt = inProgress.get();
            ensureResumePackageMatches(attempt, request);
            attempt.setLastActiveAt(now);
            Attempt saved = attempts.save(attempt);
            log.info("EXAM_ATTEMPT_RESUME studentId={} attemptId={} quizId={} assignmentId={}", persistedStudent, saved.getId(), request.quizId(), request.assignmentId());
            evict();
            AttemptResponse result = response(saved);
            complete(receipt, result, version(saved));
            return result;
        }

        try {
            ensureOpen(assignment, now);
            Optional<QuizPackage> activePackage = packages.activePackageEntity(tenant, request.quizId());
            Quiz quiz = quizzes.findByTenantIdAndId(tenant, request.quizId()).orElse(null);
            validatePackageContract(request, activePackage);
            ensureLimit(assignment, tenant, request.quizId(), persistedStudent);
            Attempt attempt = newAttempt(tenant, persistedStudent, request, assignment, quiz, activePackage, now, key);
            attempt.setLastActiveAt(now);
            Attempt saved = attempts.save(attempt);
            log.info("EXAM_ATTEMPT_START studentId={} attemptId={} quizId={} assignmentId={} mode={}", persistedStudent, saved.getId(), request.quizId(), request.assignmentId(), saved.getMode());
            evict();
            AttemptResponse result = response(saved);
            complete(receipt, result, version(saved));
            return result;
        } catch (RuntimeException exception) {
            abandon(receipt);
            throw exception;
        }
    }

    /**
     * Legacy compatibility endpoint. Learner drafts stay in browser storage until submit, so this
     * deliberately validates access without writing an attempt, event, sync receipt, or cache.
     */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AttemptResponse saveDraft(String id, AttemptDraftRequest request, UserAccessContext access) {
        log.info("EXAM_ATTEMPT_DRAFT studentId={} attemptId={}", access.userId(), id);
        Attempt attempt = mutable(id, access);
        editable(attempt);
        return response(attempt);
    }

    /** Legacy compatibility endpoint. Answers are accepted only in the final submit payload. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AnswerResultResponse saveAnswer(String id, String questionId, SaveAnswerRequest request, UserAccessContext access) {
        if (questionId.contains(".") || questionId.contains("$")) throw new BadRequestException("invalid question id");
        Attempt attempt = mutable(id, access);
        editable(attempt);
        return new AnswerResultResponse(id, questionId, false);
    }

    /** Legacy compatibility endpoint. Offline state remains client-side until final submit. */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public AttemptSyncResponse sync(String id, AttemptSyncRequest request, UserAccessContext access) {
        Attempt attempt = mutable(id, access);
        if (request != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            return new AttemptSyncResponse("conflict", response(attempt), List.of("package_hash"));
        }
        return new AttemptSyncResponse(attempt.getStatus(), response(attempt), List.of());
    }

    @Transactional("postgresTransactionManager")
    public AttemptSubmitResponse submit(String id, SubmitAttemptRequest request, UserAccessContext access) {
        Timer.Sample sample = meterRegistry.map(Timer::start).orElse(null);
        Attempt attempt = mutable(id, access);
        if (request != null && request.packageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) throw new BadRequestException("package_hash conflict");
        String submitKey = blank(request == null ? null : request.idempotencyKey());
        LmsSyncOperationService.Receipt receipt = reserve(tenant(access.tenantId()), access.userId(), submitKey,
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
        try {
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(map(attempt.getAnswers()));
        if (request != null && request.answers() != null) request.answers().forEach((qid, answer) -> answers.put(qid, answer(qid, answer, Map.of(), now())));
        // submittedAt is accepted for wire compatibility only; expiry and audit authority are server time.
        OffsetDateTime submitted = now();
        Assignment assignment = assignment(attempt.getAssignmentId(), tenant(access.tenantId())); ensureSubmitAllowed(attempt, assignment, submitted);
        attempt.setAnswers(answers); attempt.setStatus(LmsStatePolicy.submittedAttemptState()); attempt.setSubmittedAt(submitted); attempt.setUpdatedAt(now()); attempt.setLastActiveAt(attempt.getUpdatedAt());
        if (submitKey != null) attempt.setSubmitIdempotencyKey(submitKey);
        double score; double max; double percent; boolean passed; int total;
        Optional<QuizAttemptGradingService.GradedAttempt> graded = attempt.getQuizVersionId() == null ? Optional.empty() : grading.gradeSubmission(tenant(access.tenantId()), attempt.getId(), attempt.getQuizVersionId(), raw(answers));
        if (graded.isPresent()) { var result = graded.get(); score = result.score().doubleValue(); max = result.maxScore().doubleValue(); percent = result.percent(); passed = result.passed(); total = result.totalQuestions(); attempt.setStatus(result.needsManualGrade() ? LmsStatePolicy.manualReviewAttemptState() : LmsStatePolicy.submittedAttemptState()); }
        else { Quiz quiz = quiz(attempt.getQuizId(), access); score = answers.size(); max = Math.max(1, list(quiz.getQuestionIds()).size()); percent = Math.min(100, score / max * 100); passed = percent >= 50; total = (int) Math.round(max); }
        attempt.setScore(score); attempt.setMaxScore(max); attempt.setPercent(percent); attempt.setPassed(passed); attempt.setSubmittedCount(answers.size()); attempt.setTotalQuestions(total);
        Attempt saved = attempts.save(attempt);
        log.info("EXAM_ATTEMPT_SUBMIT studentId={} attemptId={} quizId={} assignmentId={} score={}/{} percent={}% passed={}", saved.getStudentId(), saved.getId(), saved.getQuizId(), saved.getAssignmentId(), score, max, percent, passed);
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_submitted", null, null, Map.of("score", score, "percent", percent, "passed", passed), submitted);
        if (request != null) events.recordClientBatch(saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), request.clientEvents());
        project(saved, answers.size(), total, score, passed, access); evict();
        boolean resultPassed = passed;
        if (sample != null) meterRegistry.ifPresent(registry -> sample.stop(registry.timer("erg.lms.attempt.submit", "result", resultPassed ? "passed" : "failed")));
            AttemptSubmitResponse result = new AttemptSubmitResponse(score, max, percent, passed);
            complete(receipt, result, version(saved));
            return result;
        } catch (RuntimeException exception) {
            abandon(receipt);
            throw exception;
        }
    }

    private Assignment assignment(String id, String tenant) { return assignments.findByTenantIdAndId(tenant, id).orElseThrow(() -> new ResourceNotFoundException("assignment not found")); }
    private Quiz quiz(String id, UserAccessContext access) { return quizzes.findByTenantIdAndId(tenant(access.tenantId()), id).orElseThrow(() -> new ResourceNotFoundException("quiz not found")); }
    private Attempt mutable(String id, UserAccessContext access) { Attempt a = attempts.findByTenantIdAndId(tenant(access.tenantId()), id).orElseThrow(() -> new ResourceNotFoundException("attempt not found")); ensureAttemptAccess(a, access); return a; }
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
        if (minutes != null && minutes > 0) {
            OffsetDateTime durationDeadline = startedAt.plusMinutes(minutes);
            result = result == null || durationDeadline.isBefore(result) ? durationDeadline : result;
        }
        return result;
    }

    private int questionCount(Quiz quiz) {
        return quiz == null || quiz.getQuestionIds() == null ? 0 : quiz.getQuestionIds().size();
    }
    private void ensureOpen(Assignment a, OffsetDateTime now) { if (a.getStartAt() != null && now.isBefore(a.getStartAt())) throw new BadRequestException("assignment has not started"); if (a.getDueAt() != null && now.isAfter(a.getDueAt())) throw new BadRequestException("assignment is past due"); }
    private void ensureLimit(Assignment a, String tenant, String quiz, String student) { Integer limit = a.getAttemptLimit(); if (limit != null && limit > 0 && attempts.countCompletedForLimit(tenant, a.getId(), quiz, student) >= limit) throw new ConflictException("attempt limit reached"); }
    private void ensureSubmitAllowed(Attempt attempt, Assignment assignment, OffsetDateTime submitted) {
        OffsetDateTime snapshotDeadline = snapshotDeadline(attempt);
        if (snapshotDeadline != null && submitted.isAfter(snapshotDeadline)) {
            Integer snapshotMinutes = snapshotInteger(attempt, "durationMinutes");
            if (snapshotMinutes != null && snapshotMinutes > 0 && attempt.getStartedAt() != null
                    && submitted.isAfter(attempt.getStartedAt().plusMinutes(snapshotMinutes))) {
                throw new BadRequestException("attempt duration exceeded");
            }
            throw new BadRequestException("assignment is past due");
        }
        if (snapshotDeadline == null && assignment.getDueAt() != null && submitted.isAfter(assignment.getDueAt())) {
            throw new BadRequestException("assignment is past due");
        }
        Integer minutes = assignment.getMaxDurationMinutes();
        if (snapshotDeadline == null && minutes != null && minutes > 0 && attempt.getStartedAt() != null
                && submitted.isAfter(attempt.getStartedAt().plusMinutes(minutes))) {
            throw new BadRequestException("attempt duration exceeded");
        }
    }
    private void ensureAttemptAccess(Attempt a, UserAccessContext access) { Assignment assignment = assignment(a.getAssignmentId(), tenant(access.tenantId())); ensureClassAccess(assignment.getAcademicClassId(), access); }
    private void ensureClassAccess(String classId, UserAccessContext access) { if (classId == null || classId.isBlank()) return; Classroom c = classrooms.findByTenantIdAndId(tenant(access.tenantId()), classId).orElseThrow(() -> new ResourceNotFoundException("class not found")); if (!access.canAccessClass(classId) || !access.canAccessEducationUnit(c.getSchoolId())) throw new ForbiddenException("You can only access LMS data in managed centers/classes"); }
    private boolean assigned(Assignment a, String student) { if (a.getStudentIds() != null && !a.getStudentIds().isEmpty()) return a.getStudentIds().contains(student); return students.findFirstByTenantIdAndAuthUserId(tenant(a.getTenantId()), student).map(s -> Objects.equals(s.getAcademicClassId(), a.getAcademicClassId())).orElse(true); }
    private void editable(Attempt a) { if (!LmsStatePolicy.attemptIsEditable(a.getStatus())) throw new BadRequestException("attempt already submitted"); }
    private int totalQuestions(Map<String,Object> json) { Object q = json == null ? null : json.get("quiz"); if (!(q instanceof Map<?,?> qm)) return 0; Object sections = ((Map<?,?>) qm).get("sections"); if (!(sections instanceof List<?> list)) return 0; return list.stream().filter(Map.class::isInstance).mapToInt(x -> { Object qs = ((Map<?,?>) x).get("questions"); return qs instanceof List<?> l ? l.size() : 0; }).sum(); }
    private String mode(String kind) { return "test".equalsIgnoreCase(kind) || "testing".equalsIgnoreCase(kind) ? "testing" : "training"; }
    private Map<String,Object> raw(Map<String,com.erg.ergspringboot.model.lms.AttemptAnswer> answers) { Map<String,Object> out = new LinkedHashMap<>(); answers.forEach((k,v) -> out.put(k, v == null ? null : v.getAnswer())); return out; }
    private com.erg.ergspringboot.model.lms.AttemptAnswer answer(String id, Object value, Map<String,Object> client, OffsetDateTime at) { return new com.erg.ergspringboot.model.lms.AttemptAnswer(id, value, client, at); }
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
    private void project(Attempt a, int answered, int total, double score, boolean passed, UserAccessContext access) { try { String t = tenant(access.tenantId()); Assignment assignment = assignments.findByTenantIdAndId(t, a.getAssignmentId()).orElse(null); String classId = assignment == null ? a.getClassId() : assignment.getAcademicClassId(); Student student = students.findByTenantIdAndId(t, a.getStudentId()).or(() -> students.findFirstByTenantIdAndAuthUserId(t, a.getStudentId())).orElse(null); String studentName = student == null ? null : student.getFullName(); String progressId = student == null || student.getAuthUserId() == null ? a.getStudentId() : student.getAuthUserId(); String title = quizzes.findByTenantIdAndId(t, a.getQuizId()).map(Quiz::getTitle).orElse(null); grading.projectProgress(t, progressId, studentName, a.getQuizId(), title, a.getAssignmentId(), classId, a.getMode(), a.getStatus(), answered, total, java.math.BigDecimal.valueOf(score), passed, a.getStartedAt(), a.getSubmittedAt()); } catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(LmsAttemptUseCase.class).warn("Failed to project student_progress for attempt {}: {}", a.getId(), ex.getMessage()); } }
    private void evict() { Runnable r = () -> READ_CACHES.forEach(n -> { Cache c = caches.getCache(n); if (c != null) c.clear(); }); if (!TransactionSynchronizationManager.isSynchronizationActive()) { r.run(); return; } TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { r.run(); } }); }
    private LmsSyncOperationService.Receipt reserve(String tenant, String account, String key, String type,
                                                     String entityId, Object payload, Long expectedVersion) {
        if (sync == null || key == null || key.isBlank()) return null;
        return sync.reserve(tenant, account, key, key, type, entityId, payload, expectedVersion).orElse(null);
    }
    private void complete(LmsSyncOperationService.Receipt receipt, Object result, long version) {
        if (receipt != null) sync.complete(receipt, result, version);
    }
    private void abandon(LmsSyncOperationService.Receipt receipt) {
        if (receipt != null) sync.abandon(receipt);
    }
    private void checkVersion(String operationId, String key, String entityType, String entityId,
                              Long expected, long actual) {
        if (expected != null && expected.longValue() != actual) {
            throw new SyncConflictException(new com.erg.ergspringboot.common.response.sync.SyncMutationResponses.ConflictPayload(
                    "STALE_WRITE", operationId, key, entityType, entityId, expected, actual,
                    sync == null ? OffsetDateTime.now(ZoneOffset.UTC) : sync.serverTime(), true),
                    "Attempt has been updated by another request.");
        }
    }
    private long version(Attempt attempt) { return attempt.getRecordVersion() == null ? 0L : attempt.getRecordVersion(); }
    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private double value(Double value) { return value == null ? 0 : value; }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
