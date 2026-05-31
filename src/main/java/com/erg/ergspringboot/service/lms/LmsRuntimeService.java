package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AnswerResultResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AssignmentProgressResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AssignmentResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptDraftRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSubmitResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptSyncResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ContentScope;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizDetailResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizPackageResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.QuizResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SaveAnswerRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentAssignmentListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentScoreItem;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StudentScoreListResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.SubmitAttemptRequest;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsRuntimeService {

    private final LmsClassroomService classroomService;
    private final LmsAssignmentService assignmentService;
    private final LmsQuizService quizService;
    private final LmsAttemptService attemptService;
    private final LmsStudentService studentService;

    public LmsRuntimeService(
            LmsClassroomService classroomService,
            LmsAssignmentService assignmentService,
            LmsQuizService quizService,
            LmsAttemptService attemptService,
            LmsStudentService studentService
    ) {
        this.classroomService = classroomService;
        this.assignmentService = assignmentService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.studentService = studentService;
    }

    public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        List<AssignmentResponse> items = assignmentService.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(item -> assignedToStudent(item, studentId))
                .filter(item -> classAllowed(item.getAcademicClassId(), access))
                .sorted(Comparator.comparing(Assignment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAssignmentResponse)
                .toList();
        return new StudentAssignmentListResponse(items);
    }

    public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        List<StudentScoreItem> items = assignmentService.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(assignment -> assignedToStudent(assignment, studentId))
                .filter(assignment -> classAllowed(assignment.getAcademicClassId(), access))
                .map(assignment -> {
                    List<AttemptResponse> recent = attemptService
                            .findTop3ByAssignmentAndStudent(tenantId, assignment.getId(), studentId)
                            .stream()
                            .map(this::toAttemptResponse)
                            .toList();
                    double bestScore = recent.stream()
                            .mapToDouble(AttemptResponse::score)
                            .max()
                            .orElse(0);
                    return new StudentScoreItem(toAssignmentResponse(assignment), bestScore, recent);
                })
                .toList();
        return new StudentScoreListResponse(items);
    }

    public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) {
        Quiz quiz = quiz(quizId, access);
        QuizResponse response = toQuizResponse(quiz);
        QuizDetailResponse detail = new QuizDetailResponse(
                response,
                nullToList(quiz.getSlides()),
                nullToMap(quiz.getSettings()),
                nullToMap(quiz.getResult()),
                nullToMap(quiz.getTheme())
        );
        return new QuizPackageResponse(response.version(), response.packageHash(), response.packageHash(), "local", "async", detail);
    }

    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) {
        return getQuizPackage(quizId, access).quiz();
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse startAttempt(String studentId, StartAttemptRequest request, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        Assignment assignment = assignment(request.assignmentId(), tenantId);
        ensureClassAccess(assignment.getAcademicClassId(), access);
        if (!Objects.equals(assignment.getQuizId(), request.quizId())) {
            throw new BadRequestException("assignment does not include quiz");
        }
        if (!assignedToStudent(assignment, studentId)) {
            throw new BadRequestException("student is not assigned");
        }
        Attempt attempt = attemptService
                .findInProgress(tenantId, request.assignmentId(), request.quizId(), studentId)
                .orElseGet(() -> newAttempt(tenantId, studentId, request));
        return toAttemptResponse(attemptService.save(attempt));
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request, UserAccessContext access) {
        Attempt attempt = mutableAttempt(attemptId, access);
        ensureEditable(attempt);
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(nullToMap(attempt.getAnswers()));
        if (request != null && request.answers() != null) {
            request.answers().forEach((questionId, answer) ->
                    answers.put(questionId, modelAnswer(questionId, answer, Map.of(), OffsetDateTime.now(ZoneOffset.UTC))));
        }
        attempt.setAnswers(answers);
        attempt.setEvents(request == null ? attempt.getEvents() : request.events());
        attempt.setClient(request == null ? attempt.getClient() : request.client());
        attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toAttemptResponse(attemptService.save(attempt));
    }

    @Transactional("postgresTransactionManager")
    public AnswerResultResponse saveAnswer(String attemptId, String questionId, SaveAnswerRequest request, UserAccessContext access) {
        if (questionId.contains(".") || questionId.contains("$")) {
            throw new BadRequestException("invalid question id");
        }
        Attempt attempt = mutableAttempt(attemptId, access);
        ensureEditable(attempt);
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(nullToMap(attempt.getAnswers()));
        answers.put(questionId, modelAnswer(
                questionId,
                request == null ? null : request.answer(),
                request == null ? Map.of() : nullToMap(request.clientResult()),
                request == null || request.answeredAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.answeredAt()
        ));
        attempt.setAnswers(answers);
        attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        attemptService.save(attempt);
        return new AnswerResultResponse(attemptId, questionId, true);
    }

    public AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request, UserAccessContext access) {
        Attempt attempt = mutableAttempt(attemptId, access);
        if (!Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            return new AttemptSyncResponse("conflict", toAttemptResponse(attempt), List.of("package_hash"));
        }
        return new AttemptSyncResponse(attempt.getStatus(), toAttemptResponse(attempt), List.of());
    }

    @Transactional("postgresTransactionManager")
    public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request, UserAccessContext access) {
        Attempt attempt = mutableAttempt(attemptId, access);
        if ("submitted".equalsIgnoreCase(attempt.getStatus())) {
            return new AttemptSubmitResponse(value(attempt.getScore()), value(attempt.getMaxScore()), value(attempt.getPercent()), Boolean.TRUE.equals(attempt.getPassed()));
        }
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(nullToMap(attempt.getAnswers()));
        if (request != null && request.answers() != null) {
            request.answers().forEach((questionId, answer) ->
                    answers.put(questionId, modelAnswer(questionId, answer, Map.of(), OffsetDateTime.now(ZoneOffset.UTC))));
        }
        Quiz quiz = quiz(attempt.getQuizId(), access);
        double score = answers.size();
        double maxScore = Math.max(1, nullToList(quiz.getQuestionIds()).size());
        double percent = Math.min(100, score / maxScore * 100);
        attempt.setAnswers(answers);
        attempt.setStatus("submitted");
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setPercent(percent);
        attempt.setPassed(percent >= 50);
        attempt.setSubmittedAt(request == null || request.submittedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : request.submittedAt());
        attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        attemptService.save(attempt);
        return new AttemptSubmitResponse(score, maxScore, percent, percent >= 50);
    }

    public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) {
        Assignment assignment = assignment(assignmentId, normalizeTenant(access.tenantId()));
        ensureClassAccess(assignment.getAcademicClassId(), access);
        List<Attempt> attempts = attemptService.findByAssignment(normalizeTenant(access.tenantId()), assignmentId);
        long submitted = attempts.stream().filter(attempt -> "submitted".equalsIgnoreCase(attempt.getStatus())).count();
        long inProgress = attempts.stream().filter(attempt -> "in_progress".equalsIgnoreCase(attempt.getStatus())).count();
        int recipientCount = assignment.getStudentIds() == null ? 0 : assignment.getStudentIds().size();
        return new AssignmentProgressResponse((int) submitted, (int) inProgress, Math.max(0, recipientCount - (int) submitted - (int) inProgress), 0);
    }

    public List<AttemptResponse> quizStudents(String quizId, UserAccessContext access) {
        quiz(quizId, access);
        return attemptService.findByQuiz(normalizeTenant(access.tenantId()), quizId).stream()
                .filter(attempt -> classAllowed(assignment(attempt.getAssignmentId(), normalizeTenant(access.tenantId())).getAcademicClassId(), access))
                .map(this::toAttemptResponse)
                .toList();
    }

    public Map<String, Object> adminPlaceholder(String name, UserAccessContext access) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("module", "lms");
        data.put("operation", name);
        data.put("status", "available");
        data.put("scope", scopePayload(access));
        if (name.endsWith("/centers") || name.endsWith("/education-units")) {
            data.put("items", educationUnitItems(access));
        } else if (name.endsWith("/classes")) {
            data.put("items", classItems(access));
        } else if (name.endsWith("/subjects")) {
            data.put("items", List.of());
        }
        return data;
    }

    public Map<String, Object> deprecatedBootstrapReplacement(String name, UserAccessContext access) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deprecated", true);
        data.put("operation", name);
        data.put("replacement", "/api/v1/sessions/current");
        data.put("message", "Use session bootstrap for current user scope and managed schools");
        data.put("scope", scopePayload(access));
        if (name.endsWith("/centers") || name.endsWith("/education-units")) {
            data.put("items", educationUnitItems(access));
        }
        return data;
    }

    @Cacheable(
            cacheNames = "lmsClassesBySchool",
            key = "{#access.tenantId(), #schoolId, #access.managedEducationUnitIds().toString()}"
    )
    public Map<String, Object> listClassesBySchool(String schoolId, UserAccessContext access) {
        String resolvedSchoolId = schoolId == null || schoolId.isBlank() ? access.currentEducationUnitId() : schoolId.trim();
        if (resolvedSchoolId == null || resolvedSchoolId.isBlank()) {
            throw new BadRequestException("schoolId is required");
        }
        ensureEducationUnitAccess(resolvedSchoolId, access);
        List<Map<String, Object>> items = classroomService
                .findByTenantIdAndSchoolIdOrderByNameAsc(normalizeTenant(access.tenantId()), resolvedSchoolId)
                .stream()
                .filter(item -> access.canAccessClass(item.getId()))
                .map(this::classPayload)
                .toList();
        return Map.of("schoolId", resolvedSchoolId, "items", items, "scope", scopePayload(access));
    }

    public Map<String, Object> normalizeScope(Map<String, Object> body, UserAccessContext access) {
        String educationUnitId = stringValue(body, "educationUnitId", stringValue(body, "centerId", access.currentEducationUnitId()));
        String academicClassId = stringValue(body, "academicClassId", stringValue(body, "classId", access.currentClassId()));
        ensureEducationUnitAccess(educationUnitId, access);
        ensureClassAccess(academicClassId, access);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("tenantId", access.tenantId());
        scope.put("educationUnitId", educationUnitId);
        scope.put("academicClassId", academicClassId);
        scope.put("managedEducationUnitIds", access.managedEducationUnitIds());
        scope.put("managedClassIds", access.managedClassIds());
        return scope;
    }

    public Map<String, Object> acceptAdminOperation(Map<String, Object> body, UserAccessContext access) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", true);
        data.put("scope", normalizeScope(body, access));
        data.put("payload", body == null ? Map.of() : body);
        return data;
    }

    public Map<String, Object> acceptAdminUpdate(String id, Map<String, Object> body, UserAccessContext access) {
        Map<String, Object> data = acceptAdminOperation(body, access);
        data.put("id", id);
        return data;
    }

    public Map<String, Object> reportExport(Map<String, String> query, UserAccessContext access) {
        ensureEducationUnitAccess(stringValue(query, "educationUnitId", query.get("centerId")), access);
        ensureClassAccess(stringValue(query, "academicClassId", query.get("classId")), access);
        return Map.of("ready", true, "format", query.getOrDefault("format", "xlsx"), "scope", normalizeScope(new LinkedHashMap<>(query), access));
    }

    private Quiz quiz(String quizId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        return quizService.findById(quizId)
                .filter(item -> tenantId.equals(normalizeTenant(item.getTenantId())))
                .orElseThrow(() -> new ResourceNotFoundException("quiz not found"));
    }

    private Assignment assignment(String assignmentId, String tenantId) {
        return assignmentService.findById(assignmentId)
                .filter(item -> normalizeTenant(tenantId).equals(normalizeTenant(item.getTenantId())))
                .orElseThrow(() -> new ResourceNotFoundException("assignment not found"));
    }

    private Attempt mutableAttempt(String attemptId, UserAccessContext access) {
        Attempt attempt = attemptService.findById(attemptId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(normalizeTenant(item.getTenantId())))
                .orElseThrow(() -> new ResourceNotFoundException("attempt not found"));
        ensureAttemptAccess(attempt, access);
        return attempt;
    }

    private Attempt newAttempt(String tenantId, String studentId, StartAttemptRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Attempt attempt = new Attempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(tenantId);
        attempt.setAssignmentId(request.assignmentId());
        attempt.setQuizId(request.quizId());
        attempt.setStudentId(studentId);
        attempt.setPackageId(request.packageId());
        attempt.setPackageHash(request.packageHash());
        attempt.setStatus("in_progress");
        attempt.setAnswers(new LinkedHashMap<>());
        attempt.setEvents(List.of());
        attempt.setClient(Map.of());
        attempt.setScore(0.0);
        attempt.setMaxScore(100.0);
        attempt.setPercent(0.0);
        attempt.setPassed(false);
        attempt.setStartedAt(now);
        attempt.setUpdatedAt(now);
        return attempt;
    }

    private void ensureEditable(Attempt attempt) {
        if ("submitted".equalsIgnoreCase(attempt.getStatus())) {
            throw new BadRequestException("attempt already submitted");
        }
    }

    private void ensureAttemptAccess(Attempt attempt, UserAccessContext access) {
        ensureClassAccess(assignment(attempt.getAssignmentId(), normalizeTenant(access.tenantId())).getAcademicClassId(), access);
    }

    private void ensureClassAccess(String classId, UserAccessContext access) {
        if (classId == null || classId.isBlank()) {
            return;
        }
        Classroom classroom = classroomService.findById(classId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(normalizeTenant(item.getTenantId())))
                .orElseThrow(() -> new ResourceNotFoundException("class not found"));
        if (!access.canAccessClass(classId) || !access.canAccessEducationUnit(classroom.getSchoolId())) {
            throw new ForbiddenException("You can only access LMS data in managed centers/classes");
        }
    }

    private void ensureEducationUnitAccess(String educationUnitId, UserAccessContext access) {
        if (!access.canAccessEducationUnit(educationUnitId)) {
            throw new ForbiddenException("You can only access LMS data in managed education units/classes");
        }
    }

    private boolean classAllowed(String classId, UserAccessContext access) {
        if (classId == null || classId.isBlank()) {
            return true;
        }
        return classroomService.findById(classId)
                .filter(item -> normalizeTenant(access.tenantId()).equals(normalizeTenant(item.getTenantId())))
                .map(item -> access.canAccessClass(classId) && access.canAccessEducationUnit(item.getSchoolId()))
                .orElse(false);
    }

    private boolean assignedToStudent(Assignment assignment, String studentId) {
        if (assignment.getStudentIds() != null && !assignment.getStudentIds().isEmpty()) {
            return assignment.getStudentIds().contains(studentId);
        }
        return studentService.findFirstByTenantIdAndAuthUserId(normalizeTenant(assignment.getTenantId()), studentId)
                .map(student -> Objects.equals(student.getAcademicClassId(), assignment.getAcademicClassId()))
                .orElse(true);
    }

    private Map<String, Object> scopePayload(UserAccessContext access) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("tenantId", access.tenantId());
        scope.put("currentEducationUnitId", access.currentEducationUnitId());
        scope.put("currentClassId", access.currentClassId());
        scope.put("managedEducationUnitIds", access.managedEducationUnitIds());
        scope.put("managedClassIds", access.managedClassIds());
        return scope;
    }

    private List<Map<String, Object>> educationUnitItems(UserAccessContext access) {
        return classroomService.findByTenantIdOrderByNameAsc(normalizeTenant(access.tenantId())).stream()
                .map(Classroom::getSchoolId)
                .filter(Objects::nonNull)
                .distinct()
                .filter(access::canAccessEducationUnit)
                .map(educationUnitId -> Map.<String, Object>of("id", educationUnitId, "name", educationUnitId))
                .toList();
    }

    private List<Map<String, Object>> classItems(UserAccessContext access) {
        return classroomService.findByTenantIdOrderByNameAsc(normalizeTenant(access.tenantId())).stream()
                .filter(item -> access.canAccessClass(item.getId()) && access.canAccessEducationUnit(item.getSchoolId()))
                .map(this::classPayload)
                .toList();
    }

    private Map<String, Object> classPayload(Classroom classroom) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", classroom.getId());
        payload.put("name", classroom.getName());
        payload.put("schoolId", classroom.getSchoolId());
        payload.put("grade", classroom.getGrade());
        payload.put("academicYear", classroom.getAcademicYear());
        return payload;
    }

    private AssignmentResponse toAssignmentResponse(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getAcademicClassId(),
                assignment.getQuizId(),
                assignment.getSubjectId(),
                nullToList(assignment.getStudentIds()),
                assignment.getDueAt(),
                assignment.getTeacherNote(),
                assignment.getStatus(),
                assignment.getRecipientMode(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }

    private QuizResponse toQuizResponse(Quiz quiz) {
        return new QuizResponse(
                quiz.getId(),
                toDtoScope(quiz.getScope()),
                quiz.getTitle(),
                quiz.getKind(),
                quiz.getSubjectId(),
                quiz.getLevelId(),
                nullToList(quiz.getTopicIds()),
                nullToList(quiz.getQuestionIds()),
                quiz.getStatus(),
                quiz.getVersion() == null ? 1 : quiz.getVersion(),
                quiz.getPackageHash(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt()
        );
    }

    private AttemptResponse toAttemptResponse(Attempt attempt) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getAssignmentId(),
                attempt.getQuizId(),
                attempt.getStudentId(),
                attempt.getPackageId(),
                attempt.getPackageHash(),
                attempt.getStatus(),
                dtoAnswers(attempt.getAnswers()),
                value(attempt.getScore()),
                value(attempt.getMaxScore()),
                value(attempt.getPercent()),
                Boolean.TRUE.equals(attempt.getPassed()),
                attempt.getStartedAt(),
                attempt.getSubmittedAt(),
                attempt.getUpdatedAt()
        );
    }

    private ContentScope toDtoScope(com.erg.ergspringboot.model.lms.ContentScope scope) {
        return scope == null ? new ContentScope("global", null) : new ContentScope(scope.getType(), scope.getSchoolId());
    }

    private Map<String, com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> dtoAnswers(
            Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers
    ) {
        Map<String, com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> result = new LinkedHashMap<>();
        nullToMap(answers).forEach((questionId, answer) -> result.put(
                questionId,
                new com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer(
                        answer == null ? null : answer.getAnswer(),
                        answer == null ? Map.of() : nullToMap(answer.getClientResult()),
                        answer == null ? null : answer.getAnsweredAt()
                )
        ));
        return result;
    }

    private com.erg.ergspringboot.model.lms.AttemptAnswer modelAnswer(
            String questionId,
            Object answer,
            Map<String, Object> clientResult,
            OffsetDateTime answeredAt
    ) {
        return new com.erg.ergspringboot.model.lms.AttemptAnswer(questionId, answer, clientResult, answeredAt);
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "erg" : tenantId.trim();
    }

    private String stringValue(Map<?, ?> body, String key, String fallback) {
        if (body == null) {
            return fallback;
        }
        Object value = body.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private double value(Double number) {
        return number == null ? 0 : number;
    }

    private <T> List<T> nullToList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private <K, V> Map<K, V> nullToMap(Map<K, V> items) {
        return items == null ? Map.of() : items;
    }
}
