package com.erg.ergspringboot.lms.internal.attempt;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.exception.*;
import com.erg.ergspringboot.model.lms.*;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.*;
import com.erg.ergspringboot.service.quiz.runtime.*;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.*;
import java.util.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

/** Transactional attempt command boundary. It owns idempotency, attempt rules and grading orchestration. */
@Service
public class LmsAttemptUseCase {
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

    public LmsAttemptUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsQuizService quizzes, LmsAttemptService attempts, LmsStudentService students,
            QuizRuntimePackageService packages, QuizAttemptGradingService grading,
            AttemptEventRecorder events, CacheManager caches) {
        this.classrooms = classrooms; this.assignments = assignments; this.quizzes = quizzes;
        this.attempts = attempts; this.students = students; this.packages = packages;
        this.grading = grading; this.events = events; this.caches = caches;
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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ensureOpen(assignment, now);
        String key = blank(idempotencyKey);
        if (key != null) {
            Optional<Attempt> existing = attempts.findByStartIdempotencyKey(tenant, request.assignmentId(), request.quizId(), persistedStudent, key);
            if (existing.isPresent()) return response(existing.get());
        }
        Attempt attempt = attempts.findInProgress(tenant, request.assignmentId(), request.quizId(), persistedStudent)
                .orElseGet(() -> { ensureLimit(assignment, tenant, request.quizId(), persistedStudent); return newAttempt(tenant, persistedStudent, request, now, key); });
        packages.activePackageEntity(tenant, request.quizId()).ifPresent(pkg -> {
            if (attempt.getQuizVersionId() == null) attempt.setQuizVersionId(pkg.getQuizVersionId());
            attempt.setGradingMode(pkg.getGradingMode()); attempt.setTotalQuestions(totalQuestions(pkg.getPackageJson()));
        });
        quizzes.findById(request.quizId()).ifPresent(q -> attempt.setMode(mode(q.getKind())));
        attempt.setLastActiveAt(now);
        Attempt saved = attempts.save(attempt); evict(); return response(saved);
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse saveDraft(String id, AttemptDraftRequest request, UserAccessContext access) {
        Attempt attempt = mutable(id, access); editable(attempt);
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(map(attempt.getAnswers()));
        if (request != null && request.answers() != null) request.answers().forEach((qid, answer) ->
                answers.put(qid, answer(qid, answer, Map.of(), OffsetDateTime.now(ZoneOffset.UTC))));
        attempt.setAnswers(answers); attempt.setEvents(request == null ? attempt.getEvents() : request.events());
        attempt.setClient(request == null ? attempt.getClient() : request.client()); attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Attempt saved = attempts.save(attempt);
        if (request != null) events.recordClientBatch(saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), request.events());
        evict(); return response(saved);
    }

    @Transactional("postgresTransactionManager")
    public AnswerResultResponse saveAnswer(String id, String questionId, SaveAnswerRequest request, UserAccessContext access) {
        if (questionId.contains(".") || questionId.contains("$")) throw new BadRequestException("invalid question id");
        Attempt attempt = mutable(id, access); editable(attempt);
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(map(attempt.getAnswers()));
        answers.put(questionId, answer(questionId, request == null ? null : request.answer(), request == null ? Map.of() : map(request.clientResult()),
                request == null || request.answeredAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.answeredAt()));
        attempt.setAnswers(answers); attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC)); Attempt saved = attempts.save(attempt);
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "answer_saved", questionId,
                request == null ? null : request.answer(), request == null ? null : request.clientResult(), request == null ? null : request.answeredAt());
        evict(); return new AnswerResultResponse(id, questionId, true);
    }

    @Transactional("postgresTransactionManager")
    public AttemptSyncResponse sync(String id, AttemptSyncRequest request, UserAccessContext access) {
        Attempt attempt = mutable(id, access);
        if (!Objects.equals(attempt.getPackageHash(), request.packageHash())) return new AttemptSyncResponse("conflict", response(attempt), List.of("package_hash"));
        events.recordClientBatch(attempt.getTenantId(), attempt.getId(), attempt.getQuizId(), attempt.getPackageId(), request.events());
        return new AttemptSyncResponse(attempt.getStatus(), response(attempt), List.of());
    }

    @Transactional("postgresTransactionManager")
    public AttemptSubmitResponse submit(String id, SubmitAttemptRequest request, UserAccessContext access) {
        Attempt attempt = mutable(id, access);
        if (request != null && request.packageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) throw new BadRequestException("package_hash conflict");
        if ("submitted".equalsIgnoreCase(attempt.getStatus())) return new AttemptSubmitResponse(value(attempt.getScore()), value(attempt.getMaxScore()), value(attempt.getPercent()), Boolean.TRUE.equals(attempt.getPassed()));
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(map(attempt.getAnswers()));
        if (request != null && request.answers() != null) request.answers().forEach((qid, answer) -> answers.put(qid, answer(qid, answer, Map.of(), OffsetDateTime.now(ZoneOffset.UTC))));
        OffsetDateTime submitted = request == null || request.submittedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.submittedAt();
        Assignment assignment = assignment(attempt.getAssignmentId(), tenant(access.tenantId())); ensureSubmitAllowed(attempt, assignment, submitted);
        attempt.setAnswers(answers); attempt.setStatus("submitted"); attempt.setSubmittedAt(submitted); attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC)); attempt.setLastActiveAt(attempt.getUpdatedAt());
        double score; double max; double percent; boolean passed; int total;
        Optional<QuizAttemptGradingService.GradedAttempt> graded = attempt.getQuizVersionId() == null ? Optional.empty() : grading.gradeSubmission(tenant(access.tenantId()), attempt.getId(), attempt.getQuizVersionId(), raw(answers));
        if (graded.isPresent()) { var result = graded.get(); score = result.score().doubleValue(); max = result.maxScore().doubleValue(); percent = result.percent(); passed = result.passed(); total = result.totalQuestions(); attempt.setStatus(result.needsManualGrade() ? "needs_review" : "submitted"); }
        else { Quiz quiz = quiz(attempt.getQuizId(), access); score = answers.size(); max = Math.max(1, list(quiz.getQuestionIds()).size()); percent = Math.min(100, score / max * 100); passed = percent >= 50; total = (int) Math.round(max); }
        attempt.setScore(score); attempt.setMaxScore(max); attempt.setPercent(percent); attempt.setPassed(passed); attempt.setSubmittedCount(answers.size()); attempt.setTotalQuestions(total);
        Attempt saved = attempts.save(attempt);
        events.record(null, saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), "attempt_submitted", null, null, Map.of("score", score, "percent", percent, "passed", passed), submitted);
        if (request != null) events.recordClientBatch(saved.getTenantId(), saved.getId(), saved.getQuizId(), saved.getPackageId(), request.clientEvents());
        project(saved, answers.size(), total, score, passed, access); evict(); return new AttemptSubmitResponse(score, max, percent, passed);
    }

    private Assignment assignment(String id, String tenant) { return assignments.findById(id).filter(a -> tenant.equals(tenant(a.getTenantId()))).orElseThrow(() -> new ResourceNotFoundException("assignment not found")); }
    private Quiz quiz(String id, UserAccessContext access) { return quizzes.findById(id).filter(q -> tenant(access.tenantId()).equals(tenant(q.getTenantId()))).orElseThrow(() -> new ResourceNotFoundException("quiz not found")); }
    private Attempt mutable(String id, UserAccessContext access) { Attempt a = attempts.findById(id).filter(x -> tenant(access.tenantId()).equals(tenant(x.getTenantId()))).orElseThrow(() -> new ResourceNotFoundException("attempt not found")); ensureAttemptAccess(a, access); return a; }
    private Attempt newAttempt(String tenant, String student, StartAttemptRequest r, OffsetDateTime now, String key) { Attempt a = new Attempt(); a.setId(IdGenerator.newId()); a.setTenantId(tenant); a.setAssignmentId(r.assignmentId()); a.setQuizId(r.quizId()); a.setStudentId(student); a.setPackageId(r.packageId()); a.setPackageHash(r.packageHash()); a.setStartIdempotencyKey(key); a.setMode("training"); a.setStatus("in_progress"); a.setSubmittedCount(0); a.setTotalQuestions(0); a.setLastActiveAt(now); a.setAnswers(new LinkedHashMap<>()); a.setEvents(List.of()); a.setClient(Map.of()); a.setScore(0.0); a.setMaxScore(100.0); a.setPercent(0.0); a.setPassed(false); a.setStartedAt(now); a.setUpdatedAt(now); return a; }
    private void ensureOpen(Assignment a, OffsetDateTime now) { if (a.getStartAt() != null && now.isBefore(a.getStartAt())) throw new BadRequestException("assignment has not started"); if (a.getDueAt() != null && now.isAfter(a.getDueAt())) throw new BadRequestException("assignment is past due"); }
    private void ensureLimit(Assignment a, String tenant, String quiz, String student) { Integer limit = a.getAttemptLimit(); if (limit != null && limit > 0 && attempts.countCompletedForLimit(tenant, a.getId(), quiz, student) >= limit) throw new ConflictException("attempt limit reached"); }
    private void ensureSubmitAllowed(Attempt a, Assignment assignment, OffsetDateTime submitted) { if (assignment.getDueAt() != null && submitted.isAfter(assignment.getDueAt())) throw new BadRequestException("assignment is past due"); Integer minutes = assignment.getMaxDurationMinutes(); if (minutes != null && minutes > 0 && a.getStartedAt() != null && submitted.isAfter(a.getStartedAt().plusMinutes(minutes))) throw new BadRequestException("attempt duration exceeded"); }
    private void ensureAttemptAccess(Attempt a, UserAccessContext access) { Assignment assignment = assignment(a.getAssignmentId(), tenant(access.tenantId())); ensureClassAccess(assignment.getAcademicClassId(), access); }
    private void ensureClassAccess(String classId, UserAccessContext access) { if (classId == null || classId.isBlank()) return; Classroom c = classrooms.findById(classId).filter(x -> tenant(access.tenantId()).equals(tenant(x.getTenantId()))).orElseThrow(() -> new ResourceNotFoundException("class not found")); if (!access.canAccessClass(classId) || !access.canAccessEducationUnit(c.getSchoolId())) throw new ForbiddenException("You can only access LMS data in managed centers/classes"); }
    private boolean assigned(Assignment a, String student) { if (a.getStudentIds() != null && !a.getStudentIds().isEmpty()) return a.getStudentIds().contains(student); return students.findFirstByTenantIdAndAuthUserId(tenant(a.getTenantId()), student).map(s -> Objects.equals(s.getAcademicClassId(), a.getAcademicClassId())).orElse(true); }
    private void editable(Attempt a) { if ("submitted".equalsIgnoreCase(a.getStatus())) throw new BadRequestException("attempt already submitted"); }
    private int totalQuestions(Map<String,Object> json) { Object q = json == null ? null : json.get("quiz"); if (!(q instanceof Map<?,?> qm)) return 0; Object sections = ((Map<?,?>) qm).get("sections"); if (!(sections instanceof List<?> list)) return 0; return list.stream().filter(Map.class::isInstance).mapToInt(x -> { Object qs = ((Map<?,?>) x).get("questions"); return qs instanceof List<?> l ? l.size() : 0; }).sum(); }
    private String mode(String kind) { return "test".equalsIgnoreCase(kind) || "testing".equalsIgnoreCase(kind) ? "testing" : "training"; }
    private Map<String,Object> raw(Map<String,com.erg.ergspringboot.model.lms.AttemptAnswer> answers) { Map<String,Object> out = new LinkedHashMap<>(); answers.forEach((k,v) -> out.put(k, v == null ? null : v.getAnswer())); return out; }
    private com.erg.ergspringboot.model.lms.AttemptAnswer answer(String id, Object value, Map<String,Object> client, OffsetDateTime at) { return new com.erg.ergspringboot.model.lms.AttemptAnswer(id, value, client, at); }
    private AttemptResponse response(Attempt a) { Map<String,com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> out = new LinkedHashMap<>(); map(a.getAnswers()).forEach((k,v) -> out.put(k, new com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer(v == null ? null : v.getAnswer(), v == null ? Map.of() : map(v.getClientResult()), v == null ? null : v.getAnsweredAt()))); return new AttemptResponse(a.getId(), a.getAssignmentId(), a.getQuizId(), a.getStudentId(), a.getPackageId(), a.getPackageHash(), a.getStatus(), out, value(a.getScore()), value(a.getMaxScore()), value(a.getPercent()), Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt(), a.getUpdatedAt()); }
    private void project(Attempt a, int answered, int total, double score, boolean passed, UserAccessContext access) { try { String t = tenant(access.tenantId()); Assignment assignment = assignments.findById(a.getAssignmentId()).orElse(null); String classId = assignment == null ? a.getClassId() : assignment.getAcademicClassId(); Student student = students.findByTenantIdAndId(t, a.getStudentId()).or(() -> students.findFirstByTenantIdAndAuthUserId(t, a.getStudentId())).orElse(null); String studentName = student == null ? null : student.getFullName(); String progressId = student == null || student.getAuthUserId() == null ? a.getStudentId() : student.getAuthUserId(); String title = quizzes.findById(a.getQuizId()).map(Quiz::getTitle).orElse(null); grading.projectProgress(t, progressId, studentName, a.getQuizId(), title, a.getAssignmentId(), classId, a.getMode(), a.getStatus(), answered, total, java.math.BigDecimal.valueOf(score), passed, a.getStartedAt(), a.getSubmittedAt()); } catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(LmsAttemptUseCase.class).warn("Failed to project student_progress for attempt {}: {}", a.getId(), ex.getMessage()); } }
    private void evict() { Runnable r = () -> READ_CACHES.forEach(n -> { Cache c = caches.getCache(n); if (c != null) c.clear(); }); if (!TransactionSynchronizationManager.isSynchronizationActive()) { r.run(); return; } TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { r.run(); } }); }
    private String tenant(String value) { return value == null || value.isBlank() ? "erg" : value.trim(); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private double value(Double value) { return value == null ? 0 : value; }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
