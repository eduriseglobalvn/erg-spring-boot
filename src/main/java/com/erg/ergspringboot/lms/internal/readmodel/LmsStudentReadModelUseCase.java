package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.lms.internal.core.LmsAssignmentService;
import com.erg.ergspringboot.lms.internal.core.LmsAttemptService;
import com.erg.ergspringboot.lms.internal.core.LmsClassroomService;
import com.erg.ergspringboot.lms.internal.core.LmsQuizService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.lms.persistence.*;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.*;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Student assignment and score projections; never mutates LMS operational aggregates. */
@Service
public class LmsStudentReadModelUseCase {
    private static final List<String> NO_SCOPE = List.of("__no-scope__");
    private final LmsClassroomService classrooms;
    private final LmsAssignmentService assignments;
    private final LmsAttemptService attempts;
    private final LmsStudentService students;
    private final LmsQuizService quizzes;
    private final com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy policy;

    public LmsStudentReadModelUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsAttemptService attempts, LmsStudentService students, LmsQuizService quizzes,
            com.erg.ergspringboot.lms.LmsAssignmentAccessPolicy policy) {
        this.classrooms = classrooms; this.assignments = assignments; this.attempts = attempts;
        this.students = students; this.quizzes = quizzes; this.policy = policy;
    }

    @Cacheable(
            cacheNames = "lmsStudentAssignments",
            key = "{#access.tenantId(), #studentId, #access.scopeFingerprint(), #page, #size}",
            unless = "#result == null"
    )
    public StudentAssignmentListResponse listAssignments(String studentId, UserAccessContext access, int page, int size) {
        int safePage = Math.max(0, page); int safeSize = Math.max(1, Math.min(size, 100)); String tenant = tenant(access.tenantId());
        Student student = students.findFirstByTenantIdAndAuthUserId(tenant, studentId).orElse(null);
        Page<Assignment> source = assignments.findStudentAssignments(tenant, student == null ? studentId : student.getId(),
                student == null ? null : student.getAcademicClassId(), PageRequest.of(safePage, safeSize));
        Map<String, Classroom> classes = classrooms(tenant, source.getContent());
        List<AssignmentResponse> result = source.getContent().stream().filter(a -> allowed(a.getAcademicClassId(), access, classes)).map(this::assignment).toList();
        return new StudentAssignmentListResponse(result, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }

    public StudentScoreListResponse listScores(String studentId, UserAccessContext access, int page, int size) {
        int safePage = Math.max(0, page); int safeSize = Math.max(1, Math.min(size, 100)); String tenant = tenant(access.tenantId());
        // Assignments and attempts store Student.id; resolve the caller's Student before every lookup.
        Student student = students.findFirstByTenantIdAndAuthUserId(tenant, studentId).orElse(null);
        String learnerKey = student == null ? studentId : student.getId();
        String classId = student == null ? null : student.getAcademicClassId();
        Page<Assignment> source = assignments.findStudentAssignments(tenant, learnerKey, classId, PageRequest.of(safePage, safeSize));
        Map<String, Classroom> classes = classrooms(tenant, source.getContent()); List<Assignment> filtered = source.getContent().stream().filter(a -> allowed(a.getAcademicClassId(), access, classes)).toList();
        Map<String, List<AttemptResponse>> recent = attempts.findTop3ByAssignmentsAndStudent(tenant, filtered.stream().map(Assignment::getId).toList(), learnerKey).stream().map(this::attempt).collect(java.util.stream.Collectors.groupingBy(AttemptResponse::assignmentId));
        List<StudentScoreItem> result = filtered.stream().map(a -> { List<AttemptResponse> items = recent.getOrDefault(a.getId(), List.of()); double best = items.stream().mapToDouble(AttemptResponse::score).max().orElse(0); return new StudentScoreItem(assignment(a), best, items); }).toList();
        return new StudentScoreListResponse(result, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }

    public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) {
        Assignment assignment = policy.readableAssignment(access.tenantId(), assignmentId, access);
        var progress = attempts.summarizeAssignmentProgress(tenant(access.tenantId()), assignmentId);
        int submitted = progress.submittedCount();
        int inProgress = progress.inProgressCount();
        int recipients = assignment.getStudentIds() == null ? 0 : assignment.getStudentIds().size();
        return new AssignmentProgressResponse(submitted, inProgress,
                Math.max(0, recipients - submitted - inProgress), progress.needsReviewCount());
    }

    public List<AttemptResponse> quizStudents(String quizId, UserAccessContext access) {
        quizzes.findByTenantIdAndId(tenant(access.tenantId()), quizId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("quiz not found"));
        String tenant = tenant(access.tenantId());
        var source = attempts.findQuizAttemptSummaries(tenant, quizId);
        Map<String, Assignment> byId = assignments.findByTenantIdAndIdIn(tenant,
                        source.stream().map(com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection::assignmentId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Assignment::getId, item -> item, (a,b) -> a));
        Map<String, Classroom> classes = classrooms(tenant, new ArrayList<>(byId.values()));
        return source.stream().filter(item -> {
            Assignment assignment = byId.get(item.assignmentId());
            return assignment != null && allowed(assignment.getAcademicClassId(), access, classes);
        }).map(this::attemptSummary).toList();
    }

    public com.erg.ergspringboot.common.response.PageResponse<AttemptResponse> quizStudentsPage(
            String quizId, UserAccessContext access, int page, int size) {
        String tenant = tenant(access.tenantId());
        quizzes.findByTenantIdAndId(tenant, quizId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("quiz not found"));
        var request = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 200));
        List<String> classIds = list(access.managedClassIds());
        List<String> unitIds = list(access.managedEducationUnitIds());
        // An empty IN list is invalid SQL on some dialects; the sentinel id matches no row.
        var result = attempts.findScopedQuizAttemptSummaries(tenant, quizId,
                classIds.contains("*"), classIds.isEmpty() ? NO_SCOPE : classIds,
                unitIds.contains("*"), unitIds.isEmpty() ? NO_SCOPE : unitIds, request);
        return new com.erg.ergspringboot.common.response.PageResponse<>(
                result.getContent().stream().map(this::attemptSummary).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }

    // List view: no jsonb answers/packageId/packageHash (not selected by the projection).
    private AttemptResponse attemptSummary(com.erg.ergspringboot.lms.persistence.projection.AttemptSummaryProjection a) {
        return new AttemptResponse(a.id(), a.assignmentId(), a.quizId(), a.studentId(), null, null,
                a.status(), Map.of(), value(a.score()), value(a.maxScore()), value(a.percent()),
                Boolean.TRUE.equals(a.passed()), a.startedAt(), a.submittedAt(), a.updatedAt());
    }

    private boolean allowed(String classId, UserAccessContext access, Map<String, Classroom> classes) { if (classId == null || classId.isBlank()) return true; Classroom c = classes.get(classId); return c != null && tenant(access.tenantId()).equals(tenant(c.getTenantId())) && access.canAccessClass(classId) && access.canAccessEducationUnit(c.getSchoolId()); }
    private Map<String, Classroom> classrooms(String tenant, List<Assignment> source) { List<String> ids = source.stream().map(Assignment::getAcademicClassId).filter(id -> id != null && !id.isBlank()).distinct().toList(); return classrooms.findByTenantIdAndIdIn(tenant, ids).stream().collect(java.util.stream.Collectors.toMap(Classroom::getId, x -> x, (a,b) -> a)); }
    private AssignmentResponse assignment(Assignment a) { return new AssignmentResponse(a.getId(), a.getAcademicClassId(), a.getQuizId(), a.getSubjectId(), List.of(), a.getDueAt(), a.getTeacherNote(), a.getStatus(), a.getRecipientMode(), a.getCreatedAt(), a.getUpdatedAt(), a.isOfflineAllowed()); }
    private AttemptResponse attempt(Attempt a) { Map<String, com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> answers = new LinkedHashMap<>(); map(a.getAnswers()).forEach((id, value) -> answers.put(id, new com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer(value == null ? null : value.getAnswer(), value == null ? Map.of() : map(value.getClientResult()), value == null ? null : value.getAnsweredAt()))); return new AttemptResponse(a.getId(), a.getAssignmentId(), a.getQuizId(), a.getStudentId(), a.getPackageId(), a.getPackageHash(), a.getStatus(), answers, value(a.getScore()), value(a.getMaxScore()), value(a.getPercent()), Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt(), a.getUpdatedAt(), com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptPolicySnapshotResponse.from(a.getPolicySnapshot())); }
    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private double value(Double value) { return value == null ? 0 : value; }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
