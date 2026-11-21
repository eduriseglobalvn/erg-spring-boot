package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.AuthenticatedTenant;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentResourceRepository;
import com.erg.ergspringboot.lms.internal.core.LmsAssignmentService;
import com.erg.ergspringboot.lms.internal.core.LmsClassroomService;
import com.erg.ergspringboot.lms.internal.core.LmsStudentService;
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
    private final LmsStudentIdentityLookup identities;

    /** Explicit wiring (tests): the learner lookup is then uncached. */
    public LmsAssignmentAccessPolicy(LmsAssignmentService assignments, LmsClassroomService classrooms,
            LmsStudentService students, AssignmentRecipientRepository recipients,
            AssignmentResourceRepository resources, LmsCallerPermissions permissions) {
        this(assignments, classrooms, students, recipients, resources, permissions, new LmsStudentIdentityLookup(students));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LmsAssignmentAccessPolicy(LmsAssignmentService assignments, LmsClassroomService classrooms,
            LmsStudentService students, AssignmentRecipientRepository recipients,
            AssignmentResourceRepository resources, LmsCallerPermissions permissions,
            LmsStudentIdentityLookup identities) {
        this.assignments = assignments;
        this.classrooms = classrooms;
        this.students = students;
        this.recipients = recipients;
        this.resources = resources;
        this.permissions = permissions;
        this.identities = identities;
    }

    /** The cached login -> student lookup, shared with the other learner hot paths. */
    public LmsStudentIdentityLookup identities() {
        return identities;
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

    /** A detached read-only snapshot (60 s cache, see {@link LmsStudentIdentityLookup}); do not persist it. */
    public Optional<Student> learner(String tenantId, String authUserId) {
        if (authUserId == null || authUserId.isBlank()) return Optional.empty();
        String tenant = tenant(tenantId);
        var identity = identities.byAuthUser(tenant, authUserId);
        return identity.exists() ? Optional.of(identity.toStudent(tenant)) : Optional.empty();
    }

    /** The persisted learner key: {@code Student.id} when linked, otherwise the auth user id (legacy rows). */
    public String learnerKey(String tenantId, String authUserId) {
        return learner(tenantId, authUserId).map(Student::getId).orElse(authUserId);
    }

    /**
     * Same rule as the student assignment SQL: saved recipient rows decide when the assignment has any; only an
     * assignment without rows falls back to the learner's class or the legacy {@code student_ids} snapshot.
     */
    public boolean isRecipient(Assignment assignment, Student learner, String authUserId) {
        String tenantId = assignment.getTenantId();
        if (learner != null && recipients.findFirstByTenantIdAndAssignmentIdAndStudentId(
                tenantId, assignment.getId(), learner.getId()).isPresent()) {
            return true;
        }
        if (recipients.existsByTenantIdAndAssignmentId(tenantId, assignment.getId())) return false;
        String classId = blank(assignment.getAcademicClassId());
        if (learner != null && classId != null && classId.equals(learner.getAcademicClassId())) return true;
        // Legacy snapshot, asked in SQL: the jsonb attribute is lazy and the assignment may be detached here.
        return recipients.legacySnapshotNames(assignment.getId(), learner == null ? null : learner.getId(), authUserId);
    }

    /** Recipient rows when the assignment has any, else the legacy {@code student_ids} snapshot size. */
    public int recipientCount(Assignment assignment) {
        long rows = recipients.countByAssignmentId(assignment.getId());
        if (rows > 0) return (int) rows;
        return recipients.countLegacyStudentIds(assignment.getId()); // SQL count: the lazy jsonb snapshot may be detached here
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

    /** Visible assignments distributing the quiz to this learner (offline result import); the recipient rule runs in SQL. */
    public List<Assignment> visibleAssignmentsFor(String tenantId, String quizId, Student learner) {
        String tenant = tenant(tenantId);
        List<String> ids = resources.findVisibleAssignmentIdsForLearner(tenant, quizId, null, learner.getId(),
                blank(learner.getAcademicClassId()), blank(learner.getAuthUserId()));
        return assignments.findByTenantIdAndIdIn(tenant, ids).stream()
                .filter(assignment -> LmsStatePolicy.assignmentIsVisible(assignment.getStatus()))
                .toList();
    }

    /** Resolves an exported (class code, student code) pair inside the caller's tenant; both must agree. */
    public Student importedLearner(String tenantId, String classCode, String studentCode) {
        List<Student> candidates = students.findByTenantIdAndStudentCode(tenant(tenantId), studentCode);
        if (candidates.isEmpty()) throw new ResourceNotFoundException("student not found");
        // The code is unique per school, so the class name picks the student among schools.
        // shortcut: two schools with the same class name and student code stay ambiguous, add the school to the export when portable results are used across schools
        List<Student> matching = candidates.stream().filter(student -> {
            Classroom classroom = blank(student.getAcademicClassId()) == null ? null
                    : classrooms.findByTenantIdAndId(tenant(tenantId), student.getAcademicClassId()).orElse(null);
            return classroom != null && classroom.getName() != null && classroom.getName().trim().equalsIgnoreCase(classCode.trim());
        }).toList();
        if (matching.isEmpty()) {
            throw new com.erg.ergspringboot.exception.BadRequestException("class code does not match the student's class");
        }
        if (matching.size() > 1) {
            throw new com.erg.ergspringboot.exception.BadRequestException("student code and class code match students in more than one school");
        }
        return matching.get(0);
    }

    /** Whether any assignment visible to the learner distributes the quiz (used by the learner package endpoint). */
    public boolean isAssignedQuiz(String tenantId, String quizId, Student learner, String authUserId) {
        return isAssignedQuiz(tenantId, quizId, null, learner, authUserId);
    }

    /**
     * One exists query (same recipient + visibility rule as {@link #isRecipient} and the student assignment SQL);
     * {@code assignmentId} narrows it to that assignment, null means any visible assignment of the quiz.
     */
    public boolean isAssignedQuiz(String tenantId, String quizId, String assignmentId, Student learner, String authUserId) {
        return assignments.existsVisibleQuizForLearner(tenant(tenantId), quizId, assignmentId,
                learner == null ? null : learner.getId(), learner == null ? null : blank(learner.getAcademicClassId()),
                blank(authUserId));
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
        // A class named in the scope (a CLASS assignment, or a class the teacher teaches) carries its school:
        // a timetable-only teacher has no standing school scope (BUG-A-050).
        if (access.managedClassIds().contains(classId)) return;
        if (!access.canAccessClass(classId) || !access.canAccessEducationUnit(classroom.getSchoolId())) {
            throw new ForbiddenException(SCOPE_DENIED);
        }
    }

    private String tenant(String value) { return AuthenticatedTenant.require(value); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
