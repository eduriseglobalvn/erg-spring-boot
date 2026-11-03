package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.model.lms.Attempt;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.lms.AssignmentRecipientRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.service.lms.LmsAssignmentService;
import com.erg.ergspringboot.service.lms.LmsClassroomService;
import com.erg.ergspringboot.service.lms.LmsStudentService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Single assignment/attempt authorization policy shared by REST and GraphQL LMS entry points.
 *
 * <p>Learner identity: the authenticated user id is resolved to its {@link Student} row
 * ({@code Student.authUserId}) before any comparison with assignment recipients or attempt owners,
 * which store {@code Student.id}. A caller without a linked Student is never treated as assigned.
 */
@Component
public class LmsAssignmentAccessPolicy {
    /** Explicit teacher/review permissions that allow acting on another learner's attempt. */
    public static final String[] REVIEW_PERMISSIONS = {"lms.attempt.grade", "lms.grade.write", "lms.manage", "*"};
    private static final String SCOPE_DENIED = "You can only access LMS data in managed centers/classes";

    private final LmsAssignmentService assignments;
    private final LmsClassroomService classrooms;
    private final LmsStudentService students;
    private final AssignmentRecipientRepository recipients;
    private final AssignmentResourceRepository resources;
    private final LmsCallerPermissions permissions;

    public LmsAssignmentAccessPolicy(LmsAssignmentService assignments, LmsClassroomService classrooms,
            LmsStudentService students, AssignmentRecipientRepository recipients,
            AssignmentResourceRepository resources, LmsCallerPermissions permissions) {
        this.assignments = assignments;
        this.classrooms = classrooms;
        this.students = students;
        this.recipients = recipients;
        this.resources = resources;
        this.permissions = permissions;
    }

    /** A quiz the assignment distributes, with the version pinned at assign time (null for legacy rows). */
    public record AssignedQuiz(String quizId, String quizVersionId) { }

    public Assignment assignment(String tenantId, String assignmentId) {
        return assignments.findByTenantIdAndId(tenant(tenantId), assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("assignment not found"));
    }

    /** Loads an assignment and verifies class + education-unit scope before any read model is queried. */
    public Assignment readableAssignment(String tenantId, String assignmentId, UserAccessContext access) {
        Assignment assignment = assignment(tenantId, assignmentId);
        ensureManagedScope(assignment, access);
        return assignment;
    }

    public Optional<Student> learner(String tenantId, String authUserId) {
        if (authUserId == null || authUserId.isBlank()) return Optional.empty();
        return students.findFirstByTenantIdAndAuthUserId(tenant(tenantId), authUserId);
    }

    /** The persisted learner key: {@code Student.id} when linked, otherwise the auth user id (legacy rows). */
    public String learnerKey(String tenantId, String authUserId) {
        return learner(tenantId, authUserId).map(Student::getId).orElse(authUserId);
    }

    /** Whether the resolved learner is a recipient of the assignment. Fails closed without a Student. */
    public boolean isRecipient(Assignment assignment, Student learner, String authUserId) {
        List<String> studentIds = assignment.getStudentIds();
        if (studentIds != null && !studentIds.isEmpty()) {
            return (learner != null && studentIds.contains(learner.getId()))
                    || (authUserId != null && studentIds.contains(authUserId));
        }
        if (learner == null) return false;
        if (recipients.findFirstByTenantIdAndAssignmentIdAndStudentId(
                assignment.getTenantId(), assignment.getId(), learner.getId()).isPresent()) {
            return true;
        }
        String classId = blank(assignment.getAcademicClassId());
        return classId != null && classId.equals(learner.getAcademicClassId());
    }

    /** Resolves the quiz inside the assignment (primary quiz or any {@code assignment_resources} row). */
    public Optional<AssignedQuiz> assignedQuiz(Assignment assignment, String quizId) {
        if (quizId == null || quizId.isBlank()) return Optional.empty();
        Optional<AssignedQuiz> resource = resources
                .findByTenantIdAndAssignmentIdAndQuizId(assignment.getTenantId(), assignment.getId(), quizId)
                .map(row -> new AssignedQuiz(row.getQuizId(), blank(row.getQuizVersionId())));
        if (resource.isPresent()) return resource;
        return Objects.equals(assignment.getQuizId(), quizId)
                ? Optional.of(new AssignedQuiz(quizId, null))
                : Optional.empty();
    }

    /** Whether any assignment visible to the learner distributes the quiz (used by the learner package endpoint). */
    public boolean isAssignedQuiz(String tenantId, String quizId, Student learner, String authUserId) {
        String tenant = tenant(tenantId);
        java.util.Set<String> assignmentIds = new java.util.LinkedHashSet<>();
        resources.findByTenantIdAndQuizId(tenant, quizId).forEach(row -> assignmentIds.add(row.getAssignmentId()));
        assignments.findByTenantIdAndQuizId(tenant, quizId).forEach(row -> assignmentIds.add(row.getId()));
        return assignments.findByTenantIdAndIdIn(tenant, assignmentIds).stream()
                .anyMatch(assignment -> LmsStatePolicy.assignmentIsVisible(assignment.getStatus())
                        && isRecipient(assignment, learner, authUserId));
    }

    public boolean isReviewer() {
        return permissions.hasAny(REVIEW_PERMISSIONS);
    }

    /**
     * Attempt ownership: the caller must own the attempt (resolved Student.id or legacy auth id), or hold an
     * explicit review permission and class/education-unit scope over the attempt's learner.
     */
    public void ensureAttemptAccess(Attempt attempt, Assignment assignment, UserAccessContext access) {
        if (isOwner(attempt, access)) return;
        if (!isReviewer()) throw new ForbiddenException("You can only access your own attempts");
        ensureReviewScope(assignment, attempt.getStudentId(), access);
    }

    public boolean isOwner(Attempt attempt, UserAccessContext access) {
        String owner = blank(attempt.getStudentId());
        if (owner == null) return false;
        if (owner.equals(access.userId())) return true;
        return learner(access.tenantId(), access.userId()).map(Student::getId).filter(owner::equals).isPresent();
    }

    /** Review scope: the assignment class, or the learner's own class for class-less assignments. */
    public void ensureReviewScope(Assignment assignment, String studentId, UserAccessContext access) {
        String classId = blank(assignment.getAcademicClassId());
        if (classId == null && studentId != null) {
            classId = students.findByTenantIdAndId(tenant(access.tenantId()), studentId)
                    .map(Student::getAcademicClassId).map(this::blank).orElse(null);
        }
        if (classId == null) {
            if (!access.managedClassIds().contains("*")) throw new ForbiddenException(SCOPE_DENIED);
            return;
        }
        ensureClassScope(access.tenantId(), classId, access);
    }

    /** Manage scope for assignment writes/reads: class + education unit; class-less rows need wildcard scope or authorship. */
    public void ensureManagedScope(Assignment assignment, UserAccessContext access) {
        String classId = blank(assignment.getAcademicClassId());
        if (classId == null) {
            if (access.managedClassIds().contains("*") || Objects.equals(assignment.getAssignedBy(), access.userId())) return;
            throw new ForbiddenException(SCOPE_DENIED);
        }
        ensureClassScope(access.tenantId(), classId, access);
    }

    public void ensureClassScope(String tenantId, String classId, UserAccessContext access) {
        if (blank(classId) == null) return;
        Classroom classroom = classrooms.findByTenantIdAndId(tenant(tenantId), classId)
                .orElseThrow(() -> new ResourceNotFoundException("class not found"));
        if (!access.canAccessClass(classId) || !access.canAccessEducationUnit(classroom.getSchoolId())) {
            throw new ForbiddenException(SCOPE_DENIED);
        }
    }

    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
