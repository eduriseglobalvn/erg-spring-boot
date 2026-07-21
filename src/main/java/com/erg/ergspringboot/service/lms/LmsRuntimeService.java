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
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AccessScopeResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ClassItem;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ClassesBySchoolResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.EducationUnitItem;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.EducationUnitListResponse;
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
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.utils.IdGenerator;
import com.erg.ergspringboot.lms.internal.attempt.LmsAttemptUseCase;
import com.erg.ergspringboot.lms.internal.classroom.LmsClassroomQueryUseCase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsRuntimeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LmsRuntimeService.class);
    private static final List<String> ATTEMPT_READ_CACHES = List.of(
            "lmsHomeworkWorkspace",
            "lmsClassWorkspace",
            "lmsAssignmentProgress",
            "studentDashboard",
            "quizAssignmentReport",
            "quizQuestionAnalytics"
    );

    private final LmsClassroomService classroomService;
    private final LmsAssignmentService assignmentService;
    private final LmsQuizService quizService;
    private final LmsAttemptService attemptService;
    private final LmsStudentService studentService;
    private final com.erg.ergspringboot.service.quiz.runtime.QuizRuntimePackageService runtimePackageService;
    private final com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService attemptGradingService;
    private final com.erg.ergspringboot.service.quiz.runtime.AttemptEventRecorder attemptEventRecorder;
    private final CacheManager cacheManager;
    private final LmsAttemptUseCase attemptCommands;
    private final LmsClassroomQueryUseCase classroomQueries;

    public LmsRuntimeService(
            LmsClassroomService classroomService,
            LmsAssignmentService assignmentService,
            LmsQuizService quizService,
            LmsAttemptService attemptService,
            LmsStudentService studentService,
            com.erg.ergspringboot.service.quiz.runtime.QuizRuntimePackageService runtimePackageService,
            com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService attemptGradingService,
            com.erg.ergspringboot.service.quiz.runtime.AttemptEventRecorder attemptEventRecorder,
            CacheManager cacheManager
    ) {
        this.classroomService = classroomService;
        this.assignmentService = assignmentService;
        this.quizService = quizService;
        this.attemptService = attemptService;
        this.studentService = studentService;
        this.runtimePackageService = runtimePackageService;
        this.attemptGradingService = attemptGradingService;
        this.attemptEventRecorder = attemptEventRecorder;
        this.cacheManager = cacheManager;
        this.attemptCommands = new LmsAttemptUseCase(
                classroomService, assignmentService, quizService, attemptService, studentService,
                runtimePackageService, attemptGradingService, attemptEventRecorder, cacheManager);
        this.classroomQueries = new LmsClassroomQueryUseCase(classroomService);
    }

    public StudentAssignmentListResponse listStudentAssignments(String studentId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        java.util.Optional<String> studentAcademicClassId = studentService
                .findFirstByTenantIdAndAuthUserId(tenantId, studentId)
                .map(student -> student.getAcademicClassId());
        List<Assignment> assignments = assignmentService.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<String, Classroom> classroomsById = classroomsById(tenantId, assignments);
        List<AssignmentResponse> items = assignments.stream()
                .filter(item -> assignedToStudent(item, studentId, studentAcademicClassId))
                .filter(item -> classAllowed(item.getAcademicClassId(), access, classroomsById))
                .sorted(Comparator.comparing(Assignment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toAssignmentResponse)
                .toList();
        return new StudentAssignmentListResponse(items);
    }

    public StudentAssignmentListResponse listStudentAssignments(
            String studentId, UserAccessContext access, int page, int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String tenantId = normalizeTenant(access.tenantId());
        Student student = studentService.findFirstByTenantIdAndAuthUserId(tenantId, studentId)
                .orElse(null);
        String canonicalStudentId = student == null ? studentId : student.getId();
        String academicClassId = student == null ? null : student.getAcademicClassId();
        Page<Assignment> assignmentPage = assignmentService.findStudentAssignments(
                tenantId, canonicalStudentId, academicClassId,
                PageRequest.of(safePage, safeSize)
        );
        Map<String, Classroom> classroomsById = classroomsById(tenantId, assignmentPage.getContent());
        List<AssignmentResponse> items = assignmentPage.getContent().stream()
                .filter(item -> classAllowed(item.getAcademicClassId(), access, classroomsById))
                .map(this::toAssignmentResponse)
                .toList();
        return new StudentAssignmentListResponse(
                items, assignmentPage.getNumber(), assignmentPage.getSize(), assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(), assignmentPage.hasNext());
    }

    public StudentScoreListResponse listStudentScores(String studentId, UserAccessContext access) {
        String tenantId = normalizeTenant(access.tenantId());
        java.util.Optional<String> studentAcademicClassId = studentService
                .findFirstByTenantIdAndAuthUserId(tenantId, studentId)
                .map(student -> student.getAcademicClassId());
        List<Assignment> assignments = assignmentService.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<String, Classroom> classroomsById = classroomsById(tenantId, assignments);
        assignments = assignments.stream()
                .filter(assignment -> assignedToStudent(assignment, studentId, studentAcademicClassId))
                .filter(assignment -> classAllowed(assignment.getAcademicClassId(), access, classroomsById))
                .toList();
        Map<String, List<AttemptResponse>> recentAttemptsByAssignment = attemptService
                .findTop3ByAssignmentsAndStudent(
                        tenantId,
                        assignments.stream().map(Assignment::getId).toList(),
                        studentId)
                .stream()
                .map(this::toAttemptResponse)
                .collect(java.util.stream.Collectors.groupingBy(AttemptResponse::assignmentId));
        List<StudentScoreItem> items = assignments.stream()
                .map(assignment -> {
                    List<AttemptResponse> recent = recentAttemptsByAssignment.getOrDefault(assignment.getId(), List.of());
                    double bestScore = recent.stream()
                            .mapToDouble(AttemptResponse::score)
                            .max()
                            .orElse(0);
                    return new StudentScoreItem(toAssignmentResponse(assignment), bestScore, recent);
                })
                .toList();
        return new StudentScoreListResponse(items);
    }

    public StudentScoreListResponse listStudentScores(
            String studentId, UserAccessContext access, int page, int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String tenantId = normalizeTenant(access.tenantId());
        String academicClassId = studentService.findFirstByTenantIdAndAuthUserId(tenantId, studentId)
                .map(item -> item.getAcademicClassId()).orElse(null);
        Page<Assignment> assignmentPage = assignmentService.findStudentAssignments(
                tenantId, studentId, academicClassId,
                PageRequest.of(safePage, safeSize)
        );
        Map<String, Classroom> classroomsById = classroomsById(tenantId, assignmentPage.getContent());
        List<Assignment> assignments = assignmentPage.getContent().stream()
                .filter(item -> classAllowed(item.getAcademicClassId(), access, classroomsById)).toList();
        Map<String, List<AttemptResponse>> recentAttemptsByAssignment = attemptService
                .findTop3ByAssignmentsAndStudent(tenantId, assignments.stream().map(Assignment::getId).toList(), studentId)
                .stream().map(this::toAttemptResponse)
                .collect(java.util.stream.Collectors.groupingBy(AttemptResponse::assignmentId));
        List<StudentScoreItem> items = assignments.stream().map(assignment -> {
            List<AttemptResponse> recent = recentAttemptsByAssignment.getOrDefault(assignment.getId(), List.of());
            double bestScore = recent.stream().mapToDouble(AttemptResponse::score).max().orElse(0);
            return new StudentScoreItem(toAssignmentResponse(assignment), bestScore, recent);
        }).toList();
        return new StudentScoreListResponse(
                items, assignmentPage.getNumber(), assignmentPage.getSize(), assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(), assignmentPage.hasNext());
    }

    public QuizPackageResponse getQuizPackage(String quizId, UserAccessContext access) {
        Quiz quiz = quiz(quizId, access);
        // Prefer the published, signed package from the version lineage (spec §3, Phase 2/3). Legacy
        // monolithic quizzes with no published version fall back to the embedded authoring blob until
        // they are (re)published — keeps existing runtime callers working during the transition.
        QuizPackageResponse published = runtimePackageService
                .activePackage(normalizeTenant(access.tenantId()), quizId)
                .orElse(null);
        if (published != null) {
            return published;
        }
        QuizResponse response = toQuizResponse(quiz);
        QuizDetailResponse detail = new QuizDetailResponse(
                response,
                nullToList(quiz.getSlides()),
                nullToMap(quiz.getSettings()),
                nullToMap(quiz.getResult()),
                nullToMap(quiz.getTheme())
        );
        return new QuizPackageResponse(response.version(), response.packageHash(), response.packageHash(), "local", "async", detail, Map.of());
    }

    public QuizDetailResponse getQuiz(String quizId, UserAccessContext access) {
        return getQuizPackage(quizId, access).quiz();
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse startAttempt(String studentId, StartAttemptRequest request, UserAccessContext access) {
        return attemptCommands.start(studentId, request, null, access);
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse startAttempt(
            String studentId,
            StartAttemptRequest request,
            String idempotencyKey,
            UserAccessContext access
    ) {
        return attemptCommands.start(studentId, request, idempotencyKey, access);
    }

    @Transactional("postgresTransactionManager")
    public AttemptResponse saveDraft(String attemptId, AttemptDraftRequest request, UserAccessContext access) {
        return attemptCommands.saveDraft(attemptId, request, access);
    }

    @Transactional("postgresTransactionManager")
    public AnswerResultResponse saveAnswer(String attemptId, String questionId, SaveAnswerRequest request, UserAccessContext access) {
        return attemptCommands.saveAnswer(attemptId, questionId, request, access);
    }

    @Transactional("postgresTransactionManager")
    public AttemptSyncResponse syncAttempt(String attemptId, AttemptSyncRequest request, UserAccessContext access) {
        return attemptCommands.sync(attemptId, request, access);
        /* legacy implementation removed; */
        /* Attempt attempt = mutableAttempt(attemptId, access);
        if (!Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            return new AttemptSyncResponse("conflict", toAttemptResponse(attempt), List.of("package_hash"));
        }
        // Replay the offline client event log idempotently into the append-only audit table (spec §1.7).
        attemptEventRecorder.recordClientBatch(
                attempt.getTenantId(), attempt.getId(), attempt.getQuizId(), attempt.getPackageId(), request.events());
        return new AttemptSyncResponse(attempt.getStatus(), toAttemptResponse(attempt), List.of()); */
    }

    @Transactional("postgresTransactionManager")
    public AttemptSubmitResponse submitAttempt(String attemptId, SubmitAttemptRequest request, UserAccessContext access) {
        return attemptCommands.submit(attemptId, request, access);
        /* Attempt attempt = mutableAttempt(attemptId, access);
        if (request != null && request.packageHash() != null && !Objects.equals(attempt.getPackageHash(), request.packageHash())) {
            throw new BadRequestException("package_hash conflict");
        }
        if ("submitted".equalsIgnoreCase(attempt.getStatus())) {
            return new AttemptSubmitResponse(value(attempt.getScore()), value(attempt.getMaxScore()), value(attempt.getPercent()), Boolean.TRUE.equals(attempt.getPassed()));
        }
        Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers = new LinkedHashMap<>(nullToMap(attempt.getAnswers()));
        if (request != null && request.answers() != null) {
            request.answers().forEach((questionId, answer) ->
                    answers.put(questionId, modelAnswer(questionId, answer, Map.of(), OffsetDateTime.now(ZoneOffset.UTC))));
        }
        OffsetDateTime submittedAt = request == null || request.submittedAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC) : request.submittedAt();
        Assignment assignment = assignment(attempt.getAssignmentId(), normalizeTenant(access.tenantId()));
        ensureAttemptSubmitAllowed(attempt, assignment, submittedAt);
        attempt.setAnswers(answers);
        attempt.setStatus("submitted");
        attempt.setSubmittedAt(submittedAt);
        attempt.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        attempt.setLastActiveAt(attempt.getUpdatedAt());

        // Prefer server-authoritative grading against the pinned published version (spec §6). When the
        // attempt predates the version lineage (no quiz_version_id) fall back to the legacy count-based
        // score so existing quizzes keep submitting.
        double score;
        double maxScore;
        double percent;
        boolean passed;
        int totalQuestions;
        var graded = attempt.getQuizVersionId() == null
                ? java.util.Optional.<com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService.GradedAttempt>empty()
                : legacyGradingRemoved(
                        normalizeTenant(access.tenantId()),
                        attempt.getId(),
                        attempt.getQuizVersionId(),
                        rawAnswersByQuestionId(answers));
        if (graded.isPresent()) {
            com.erg.ergspringboot.service.quiz.runtime.QuizAttemptGradingService.GradedAttempt result = graded.get();
            score = result.score().doubleValue();
            maxScore = result.maxScore().doubleValue();
            percent = result.percent();
            passed = result.passed();
            totalQuestions = result.totalQuestions();
            attempt.setStatus(result.needsManualGrade() ? "needs_review" : "submitted");
        } else {
            Quiz quiz = quiz(attempt.getQuizId(), access);
            score = answers.size();
            maxScore = Math.max(1, nullToList(quiz.getQuestionIds()).size());
            percent = Math.min(100, score / maxScore * 100);
            passed = percent >= 50;
            totalQuestions = (int) Math.round(maxScore);
        }
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setPercent(percent);
        attempt.setPassed(passed);
        attempt.setSubmittedCount(answers.size());
        attempt.setTotalQuestions(totalQuestions);
        Attempt savedAttempt = attemptService.save(attempt);

        // Audit the submission and refresh the live progress projection the teacher dashboard reads (§1.7).
        attemptEventRecorder.record(
                null, savedAttempt.getTenantId(), savedAttempt.getId(), savedAttempt.getQuizId(),
                savedAttempt.getPackageId(), "attempt_submitted", null, null,
                java.util.Map.of("score", score, "percent", percent, "passed", passed), submittedAt);
        if (request != null) {
            attemptEventRecorder.recordClientBatch(
                    savedAttempt.getTenantId(), savedAttempt.getId(), savedAttempt.getQuizId(),
                    savedAttempt.getPackageId(), request.clientEvents());
        }
        projectSubmitProgress(savedAttempt, answers.size(), totalQuestions, score, passed, access);
        evictAttemptReadCachesAfterCommit();
        return new AttemptSubmitResponse(score, maxScore, percent, passed); */
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
        String tenantId = normalizeTenant(access.tenantId());
        List<Attempt> attempts = attemptService.findByQuiz(tenantId, quizId);
        Map<String, Assignment> assignmentsById = assignmentService.findByTenantIdAndIdIn(
                        tenantId,
                        attempts.stream().map(Attempt::getAssignmentId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Assignment::getId, item -> item, (left, right) -> left));
        Map<String, Classroom> classroomsById = classroomsById(tenantId, new java.util.ArrayList<>(assignmentsById.values()));
        return attempts.stream()
                .filter(attempt -> {
                    Assignment assignment = assignmentsById.get(attempt.getAssignmentId());
                    return assignment != null
                            && classAllowed(assignment.getAcademicClassId(), access, classroomsById);
                })
                .map(this::toAttemptResponse)
                .toList();
    }

    public EducationUnitListResponse listEducationUnits(UserAccessContext access) {
        return classroomQueries.listEducationUnits(access);
    }

    @Cacheable(
            cacheNames = "lmsClassesBySchool",
            key = "{#access.tenantId(), #schoolId, #access.currentEducationUnitId(), #access.managedEducationUnitIds().toString()}"
    )
    public ClassesBySchoolResponse listClassesBySchool(String schoolId, UserAccessContext access) {
        return classroomQueries.listClassesBySchool(schoolId, access);
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

    private Attempt newAttempt(
            String tenantId,
            String studentId,
            StartAttemptRequest request,
            OffsetDateTime now,
            String startIdempotencyKey
    ) {
        Attempt attempt = new Attempt();
        attempt.setId(IdGenerator.newId());
        attempt.setTenantId(tenantId);
        attempt.setAssignmentId(request.assignmentId());
        attempt.setQuizId(request.quizId());
        attempt.setStudentId(studentId);
        attempt.setPackageId(request.packageId());
        attempt.setPackageHash(request.packageHash());
        attempt.setStartIdempotencyKey(startIdempotencyKey);
        attempt.setMode("training");
        attempt.setStatus("in_progress");
        attempt.setSubmittedCount(0);
        attempt.setTotalQuestions(0);
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
        return attempt;
    }

    private void ensureAssignmentOpenForStart(Assignment assignment, OffsetDateTime now) {
        if (assignment.getStartAt() != null && now.isBefore(assignment.getStartAt())) {
            throw new BadRequestException("assignment has not started");
        }
        if (assignment.getDueAt() != null && now.isAfter(assignment.getDueAt())) {
            throw new BadRequestException("assignment is past due");
        }
    }

    private void ensureAttemptLimitAvailable(Assignment assignment, String tenantId, String quizId, String studentId) {
        Integer limit = assignment.getAttemptLimit();
        if (limit == null || limit <= 0) {
            return;
        }
        long completedAttempts = attemptService.countCompletedForLimit(tenantId, assignment.getId(), quizId, studentId);
        if (completedAttempts >= limit) {
            throw new ConflictException("attempt limit reached");
        }
    }

    private void ensureAttemptSubmitAllowed(Attempt attempt, Assignment assignment, OffsetDateTime submittedAt) {
        if (assignment.getDueAt() != null && submittedAt.isAfter(assignment.getDueAt())) {
            throw new BadRequestException("assignment is past due");
        }
        Integer maxDurationMinutes = assignment.getMaxDurationMinutes();
        if (maxDurationMinutes == null || maxDurationMinutes <= 0 || attempt.getStartedAt() == null) {
            return;
        }
        OffsetDateTime deadline = attempt.getStartedAt().plusMinutes(maxDurationMinutes);
        if (submittedAt.isAfter(deadline)) {
            throw new BadRequestException("attempt duration exceeded");
        }
    }

    @SuppressWarnings("unchecked")
    private int totalQuestions(Map<String, Object> packageJson) {
        Object quiz = packageJson == null ? null : packageJson.get("quiz");
        if (!(quiz instanceof Map<?, ?> quizMap)) {
            return 0;
        }
        Object sections = ((Map<String, Object>) quizMap).get("sections");
        if (!(sections instanceof List<?> sectionList)) {
            return 0;
        }
        int total = 0;
        for (Object section : sectionList) {
            if (section instanceof Map<?, ?> sectionMap) {
                Object questions = ((Map<String, Object>) sectionMap).get("questions");
                if (questions instanceof List<?> questionList) {
                    total += questionList.size();
                }
            }
        }
        return total;
    }

    private String runtimeMode(String quizKind) {
        return "test".equalsIgnoreCase(quizKind) || "testing".equalsIgnoreCase(quizKind) ? "testing" : "training";
    }

    private void ensureEditable(Attempt attempt) {
        if ("submitted".equalsIgnoreCase(attempt.getStatus())) {
            throw new BadRequestException("attempt already submitted");
        }
    }

    /** Flattens the stored answer map (questionId -> AttemptAnswer) to questionId -> raw answer for grading. */
    private Map<String, Object> rawAnswersByQuestionId(
            Map<String, com.erg.ergspringboot.model.lms.AttemptAnswer> answers
    ) {
        Map<String, Object> raw = new LinkedHashMap<>();
        answers.forEach((questionId, answer) -> raw.put(questionId, answer == null ? null : answer.getAnswer()));
        return raw;
    }

    /**
     * Refreshes the live {@code student_progress} row the teacher dashboard/report reads (spec §1.7).
     * Best-effort: a projection failure never blocks a submit, since the authoritative attempt is already
     * persisted and the projection can be rebuilt.
     */
    private void projectSubmitProgress(
            Attempt attempt,
            int answeredCount,
            int totalQuestions,
            double score,
            boolean passed,
            UserAccessContext access
    ) {
        try {
            String tenantId = normalizeTenant(access.tenantId());
            Assignment assignment = assignmentService.findById(attempt.getAssignmentId()).orElse(null);
            String classId = assignment == null ? attempt.getClassId() : assignment.getAcademicClassId();
            com.erg.ergspringboot.model.lms.Student student = studentService
                    .findByTenantIdAndId(tenantId, attempt.getStudentId())
                    .or(() -> studentService.findFirstByTenantIdAndAuthUserId(tenantId, attempt.getStudentId()))
                    .orElse(null);
            String studentName = student == null ? null : student.getFullName();
            String progressStudentId = student == null || student.getAuthUserId() == null
                    ? attempt.getStudentId()
                    : student.getAuthUserId();
            String quizTitle = quizService.findById(attempt.getQuizId())
                    .map(quiz -> quiz.getTitle())
                    .orElse(null);
            attemptGradingService.projectProgress(
                    tenantId,
                    progressStudentId,
                    studentName,
                    attempt.getQuizId(),
                    quizTitle,
                    attempt.getAssignmentId(),
                    classId,
                    attempt.getMode(),
                    attempt.getStatus(),
                    answeredCount,
                    totalQuestions,
                    java.math.BigDecimal.valueOf(score),
                    passed,
                    attempt.getStartedAt(),
                    attempt.getSubmittedAt());
        } catch (RuntimeException ex) {
            log.warn("Failed to project student_progress for attempt {}: {}", attempt.getId(), ex.getMessage());
        }
    }

    private void evictAttemptReadCachesAfterCommit() {
        Runnable evict = () -> ATTEMPT_READ_CACHES.forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict.run();
            }
        });
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

    private AccessScopeResponse scopePayload(UserAccessContext access) {
        return new AccessScopeResponse(access.tenantId(), access.currentEducationUnitId(), access.currentClassId(),
                access.managedEducationUnitIds(), access.managedClassIds());
    }

    private List<EducationUnitItem> educationUnitItems(UserAccessContext access) {
        List<String> schoolIds = classroomService.findDistinctSchoolIdsByTenant(normalizeTenant(access.tenantId()));
        if (schoolIds == null) {
            schoolIds = classroomService.findByTenantIdOrderByNameAsc(normalizeTenant(access.tenantId())).stream()
                    .map(Classroom::getSchoolId).filter(Objects::nonNull).distinct().toList();
        }
        return schoolIds.stream()
                .filter(access::canAccessEducationUnit)
                .map(educationUnitId -> new EducationUnitItem(educationUnitId, educationUnitId))
                .toList();
    }

    private ClassItem classPayload(Classroom classroom) {
        return new ClassItem(classroom.getId(), classroom.getName(), classroom.getSchoolId(),
                classroom.getGrade(), classroom.getAcademicYear());
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

    private boolean classAllowed(String classId, UserAccessContext access, Map<String, Classroom> classroomsById) {
        if (classId == null || classId.isBlank()) {
            return true;
        }
        Classroom classroom = classroomsById.get(classId);
        return classroom != null
                && normalizeTenant(access.tenantId()).equals(normalizeTenant(classroom.getTenantId()))
                && access.canAccessClass(classId)
                && access.canAccessEducationUnit(classroom.getSchoolId());
    }

    private Map<String, Classroom> classroomsById(String tenantId, List<Assignment> assignments) {
        List<String> classIds = assignments.stream()
                .map(Assignment::getAcademicClassId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        return classroomService.findByTenantIdAndIdIn(tenantId, classIds).stream()
                .collect(java.util.stream.Collectors.toMap(Classroom::getId, item -> item, (left, right) -> left));
    }

    private boolean assignedToStudent(
            Assignment assignment,
            String studentId,
            java.util.Optional<String> studentAcademicClassId
    ) {
        if (assignment.getStudentIds() != null && !assignment.getStudentIds().isEmpty()) {
            return assignment.getStudentIds().contains(studentId);
        }
        return studentAcademicClassId
                .map(classId -> Objects.equals(classId, assignment.getAcademicClassId()))
                .orElse(true);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
