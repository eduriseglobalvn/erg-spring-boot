package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.*;
import com.erg.ergspringboot.model.lms.*;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.service.lms.*;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Student assignment and score projections; never mutates LMS operational aggregates. */
@Service
public class LmsStudentReadModelUseCase {
    private final LmsClassroomService classrooms;
    private final LmsAssignmentService assignments;
    private final LmsAttemptService attempts;
    private final LmsStudentService students;
    private final LmsQuizService quizzes;

    public LmsStudentReadModelUseCase(LmsClassroomService classrooms, LmsAssignmentService assignments,
            LmsAttemptService attempts, LmsStudentService students, LmsQuizService quizzes) {
        this.classrooms = classrooms; this.assignments = assignments; this.attempts = attempts;
        this.students = students; this.quizzes = quizzes;
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
        String classId = students.findFirstByTenantIdAndAuthUserId(tenant, studentId).map(Student::getAcademicClassId).orElse(null);
        Page<Assignment> source = assignments.findStudentAssignments(tenant, studentId, classId, PageRequest.of(safePage, safeSize));
        Map<String, Classroom> classes = classrooms(tenant, source.getContent()); List<Assignment> filtered = source.getContent().stream().filter(a -> allowed(a.getAcademicClassId(), access, classes)).toList();
        Map<String, List<AttemptResponse>> recent = attempts.findTop3ByAssignmentsAndStudent(tenant, filtered.stream().map(Assignment::getId).toList(), studentId).stream().map(this::attempt).collect(java.util.stream.Collectors.groupingBy(AttemptResponse::assignmentId));
        List<StudentScoreItem> result = filtered.stream().map(a -> { List<AttemptResponse> items = recent.getOrDefault(a.getId(), List.of()); double best = items.stream().mapToDouble(AttemptResponse::score).max().orElse(0); return new StudentScoreItem(assignment(a), best, items); }).toList();
        return new StudentScoreListResponse(result, source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }

    public AssignmentProgressResponse assignmentProgress(String assignmentId, UserAccessContext access) {
        Assignment assignment = assignments.findByTenantIdAndId(tenant(access.tenantId()), assignmentId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("assignment not found"));
        ensureClassAccess(assignment.getAcademicClassId(), access);
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
                        source.stream().map(com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection::assignmentId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Assignment::getId, item -> item, (a,b) -> a));
        Map<String, Classroom> classes = classrooms(tenant, new ArrayList<>(byId.values()));
        return source.stream().filter(item -> {
            Assignment assignment = byId.get(item.assignmentId());
            return assignment != null && allowed(assignment.getAcademicClassId(), access, classes);
        }).map(this::attemptSummary).toList();
    }

    // List view: no jsonb answers/packageId/packageHash (not selected by the projection).
    private AttemptResponse attemptSummary(com.erg.ergspringboot.repository.lms.projection.AttemptSummaryProjection a) {
        return new AttemptResponse(a.id(), a.assignmentId(), a.quizId(), a.studentId(), null, null,
                a.status(), Map.of(), value(a.score()), value(a.maxScore()), value(a.percent()),
                Boolean.TRUE.equals(a.passed()), a.startedAt(), a.submittedAt(), a.updatedAt());
    }

    private void ensureClassAccess(String classId, UserAccessContext access) {
        if (classId == null || classId.isBlank()) return;
        Classroom classroom = classrooms.findByTenantIdAndId(tenant(access.tenantId()), classId)
                .orElseThrow(() -> new com.erg.ergspringboot.exception.ResourceNotFoundException("class not found"));
        if (!access.canAccessClass(classId) || !access.canAccessEducationUnit(classroom.getSchoolId()))
            throw new com.erg.ergspringboot.exception.ForbiddenException(
                    "You can only access LMS data in managed centers/classes");
    }

    private boolean allowed(String classId, UserAccessContext access, Map<String, Classroom> classes) { if (classId == null || classId.isBlank()) return true; Classroom c = classes.get(classId); return c != null && tenant(access.tenantId()).equals(tenant(c.getTenantId())) && access.canAccessClass(classId) && access.canAccessEducationUnit(c.getSchoolId()); }
    private Map<String, Classroom> classrooms(String tenant, List<Assignment> source) { List<String> ids = source.stream().map(Assignment::getAcademicClassId).filter(id -> id != null && !id.isBlank()).distinct().toList(); return classrooms.findByTenantIdAndIdIn(tenant, ids).stream().collect(java.util.stream.Collectors.toMap(Classroom::getId, x -> x, (a,b) -> a)); }
    private AssignmentResponse assignment(Assignment a) { return new AssignmentResponse(a.getId(), a.getAcademicClassId(), a.getQuizId(), a.getSubjectId(), list(a.getStudentIds()), a.getDueAt(), a.getTeacherNote(), a.getStatus(), a.getRecipientMode(), a.getCreatedAt(), a.getUpdatedAt(), a.isOfflineAllowed()); }
    private AttemptResponse attempt(Attempt a) { Map<String, com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer> answers = new LinkedHashMap<>(); map(a.getAnswers()).forEach((id, value) -> answers.put(id, new com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptAnswer(value == null ? null : value.getAnswer(), value == null ? Map.of() : map(value.getClientResult()), value == null ? null : value.getAnsweredAt()))); return new AttemptResponse(a.getId(), a.getAssignmentId(), a.getQuizId(), a.getStudentId(), a.getPackageId(), a.getPackageHash(), a.getStatus(), answers, value(a.getScore()), value(a.getMaxScore()), value(a.getPercent()), Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt(), a.getUpdatedAt(), com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptPolicySnapshotResponse.from(a.getPolicySnapshot())); }
    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private double value(Double value) { return value == null ? 0 : value; }
    private <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
    private <K,V> Map<K,V> map(Map<K,V> value) { return value == null ? Map.of() : value; }
}
