package com.erg.ergspringboot.elearning.internal.readmodel;

import com.erg.ergspringboot.elearning.api.StudentDashboardReadScope;
import static com.erg.ergspringboot.elearning.internal.readmodel.StudentDashboardCards.*;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAnnouncementCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAssignmentCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardClassMembershipResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardCourseCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardNextActionResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardProfileResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardScoreSummaryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardSummaryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardViewerResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentEnrollment;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentEnrollmentRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentCountProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentProjection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Module-internal persistent dashboard read service; callers outside ELEARNING use {@code ElearningOperations}. */
@Service
@Transactional(readOnly = true)
public class StudentDashboardReadService {

    private static final String DASHBOARD_PERMISSION = "elearning.assignment.read_self";
    private static final int FIRST_SLICE_LIMIT = 8;
    private static final int ANNOUNCEMENT_LIMIT = 5;

    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final ErgPolicyService policyService;
    private final StudentDashboardAnnouncementReadPort announcementReadPort;

    @Autowired
    public StudentDashboardReadService(
            AssignmentRepository assignmentRepository,
            AttemptRepository attemptRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            ErgPolicyService policyService,
            StudentDashboardAnnouncementReadPort announcementReadPort,
            StudentEnrollmentRepository studentEnrollmentRepository,
            ClassroomRepository classroomRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.policyService = policyService;
        this.announcementReadPort = announcementReadPort;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.classroomRepository = classroomRepository;
    }

    public StudentDashboardReadService(
            AssignmentRepository assignmentRepository,
            AttemptRepository attemptRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            ErgPolicyService policyService,
            StudentDashboardAnnouncementReadPort announcementReadPort
    ) {
        this(
                assignmentRepository,
                attemptRepository,
                studentRepository,
                userRepository,
                policyService,
                announcementReadPort,
                null,
                null
        );
    }

    public StudentDashboardReadService(
            AssignmentRepository assignmentRepository,
            AttemptRepository attemptRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            ErgPolicyService policyService
    ) {
        this(
                assignmentRepository,
                attemptRepository,
                studentRepository,
                userRepository,
                policyService,
                StudentDashboardAnnouncementReadPort.unavailable(),
                null,
                null
        );
    }

    @Cacheable(
            cacheNames = "studentDashboard",
            key = "{#scope.userId(), #scope.tenantId(), #scope.requestedTenantId(), #scope.requestedStudentUserId(), #scope.requestedClassId()}",
            unless = "#result == null"
    )
    public StudentDashboardResponse studentDashboard(StudentDashboardReadScope scope) {
        StudentDashboardReadScope safeScope = requireScope(scope);
        assertPermission(safeScope);
        assertTenantSelfScope(safeScope);

        User user = userRepository.findWithDetailsByIdAndTenantId(safeScope.userId(), safeScope.tenantId()).orElse(null);
        Student student = studentRepository.findFirstByTenantIdAndAuthUserId(safeScope.tenantId(), safeScope.userId()).orElse(null);
        String studentId = student == null ? null : blankToNull(student.getId());
        List<StudentDashboardClassMembershipResponse> classMemberships = classMemberships(safeScope, student);
        String academicClassId = selectedClassId(safeScope, student, classMemberships);
        StudentDashboardProfileResponse profile = toProfile(safeScope, user, student, academicClassId);
        List<StudentDashboardAssignmentProjection> assignmentSlice = studentId == null
                ? List.of()
                : assignmentRepository.findStudentDashboardAssignments(
                        safeScope.tenantId(),
                        academicClassId,
                        studentId,
                        PageRequest.of(0, FIRST_SLICE_LIMIT)
                );
        StudentDashboardAssignmentCountProjection assignmentCounts = studentId == null
                ? null
                : assignmentRepository.summarizeStudentDashboardAssignments(
                        safeScope.tenantId(), academicClassId, studentId);
        // Legacy counts still run when the account has no Student row (studentId == null), so they stay (SQ-20).
        long activeAssignmentCount = assignmentCounts == null
                ? legacyActiveAssignmentCount(safeScope, academicClassId, studentId)
                : nullToZero(assignmentCounts.activeAssignmentCount());
        long pendingActionCount = assignmentCounts == null
                ? legacyPendingActionCount(safeScope, academicClassId, studentId)
                : nullToZero(assignmentCounts.pendingActionCount());
        StudentDashboardScoreSummaryResponse scoreSummary = studentId == null
                ? emptyScoreSummary()
                : toScoreSummary(attemptRepository.summarizeStudentDashboardScores(
                        safeScope.tenantId(), studentId, academicClassId));
        List<StudentDashboardCourseCardResponse> courses = toCourseCards(assignmentSlice);
        List<StudentDashboardAssignmentCardResponse> assignments = assignmentSlice.stream()
                .map(StudentDashboardCards::toAssignmentCard)
                .toList();
        List<StudentDashboardNextActionResponse> nextActions = assignmentSlice.stream()
                .filter(StudentDashboardCards::isPendingAction)
                .limit(5)
                .map(StudentDashboardCards::toNextAction)
                .toList();
        List<StudentDashboardAnnouncementCardResponse> announcements = studentId == null || academicClassId == null
                ? List.of()
                : announcementReadPort.findStudentDashboardAnnouncements(
                                safeScope.tenantId(),
                                studentId,
                                blankToNull(student.getSchoolId()),
                                academicClassId,
                                ANNOUNCEMENT_LIMIT
                        ).stream()
                        .limit(ANNOUNCEMENT_LIMIT)
                        .map(StudentDashboardCards::toAnnouncementCard)
                        .toList();

        return new StudentDashboardResponse(
                safeScope.tenantId(),
                new StudentDashboardViewerResponse(
                        safeScope.userId(),
                        safeScope.tenantId(),
                        safeScope.accountType(),
                        safeScope.accessLevel()
                ),
                profile,
                new StudentDashboardSummaryResponse(
                        courses.size(),
                        boundedInt(activeAssignmentCount),
                        boundedInt(pendingActionCount),
                        scoreSummary,
                        OffsetDateTime.now(ZoneOffset.UTC)
                ),
                courses,
                assignments,
                nextActions,
                announcements,
                academicClassId,
                classMemberships
        );
    }

    private long legacyActiveAssignmentCount(
            StudentDashboardReadScope scope,
            String academicClassId,
            String studentId
    ) {
        return assignmentRepository.countActiveStudentAssignments(scope.tenantId(), academicClassId, studentId);
    }

    private long legacyPendingActionCount(
            StudentDashboardReadScope scope,
            String academicClassId,
            String studentId
    ) {
        return assignmentRepository.countPendingStudentAssignmentActions(scope.tenantId(), academicClassId, studentId);
    }

    private List<StudentDashboardClassMembershipResponse> classMemberships(
            StudentDashboardReadScope scope,
            Student student
    ) {
        if (student == null || blankToNull(student.getId()) == null) {
            return List.of();
        }

        Map<String, StudentEnrollment> enrollmentsByClass = new LinkedHashMap<>();
        if (studentEnrollmentRepository != null) {
            studentEnrollmentRepository
                    .findByTenantIdAndStudentIdAndStatusIgnoreCaseOrderByCreatedAtAsc(
                            scope.tenantId(), student.getId(), "ENROLLED")
                    .forEach(enrollment -> {
                        String classId = blankToNull(enrollment.getClassId());
                        if (classId != null) enrollmentsByClass.putIfAbsent(classId, enrollment);
                    });
        }

        String legacyPrimaryClassId = blankToNull(student.getAcademicClassId());
        if (legacyPrimaryClassId != null && !enrollmentsByClass.containsKey(legacyPrimaryClassId)) {
            enrollmentsByClass.put(legacyPrimaryClassId, null);
        }
        if (enrollmentsByClass.isEmpty()) {
            return List.of();
        }

        Map<String, Classroom> classroomsById = new LinkedHashMap<>();
        if (classroomRepository != null && !enrollmentsByClass.isEmpty()
                && enrollmentsByClass.values().stream().anyMatch(java.util.Objects::nonNull)) {
            classroomRepository.findByTenantIdAndIdIn(scope.tenantId(), enrollmentsByClass.keySet())
                    .forEach(classroom -> classroomsById.put(classroom.getId(), classroom));
        }

        return enrollmentsByClass.entrySet().stream()
                .map(entry -> toClassMembership(student, entry.getKey(), entry.getValue(), classroomsById.get(entry.getKey())))
                .sorted(Comparator.comparing(StudentDashboardClassMembershipResponse::className, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String selectedClassId(
            StudentDashboardReadScope scope,
            Student student,
            List<StudentDashboardClassMembershipResponse> memberships
    ) {
        String requestedClassId = blankToNull(scope.requestedClassId());
        if (requestedClassId != null) {
            boolean enrolled = memberships.stream().anyMatch(membership -> requestedClassId.equals(membership.classId()));
            if (!enrolled) {
                throw new ForbiddenException("Requested class is outside the authenticated student's enrollment scope.");
            }
            return requestedClassId;
        }

        String legacyPrimaryClassId = student == null ? null : blankToNull(student.getAcademicClassId());
        if (legacyPrimaryClassId != null
                && memberships.stream().anyMatch(membership -> legacyPrimaryClassId.equals(membership.classId()))) {
            return legacyPrimaryClassId;
        }
        return memberships.isEmpty() ? null : memberships.getFirst().classId();
    }

    private void assertPermission(StudentDashboardReadScope scope) {
        if (!policyService.isAllowed(scope.toPrincipal(), DASHBOARD_PERMISSION, "elearning", scope.tenantId())) {
            throw new ForbiddenException("GraphQL student dashboard permission is missing.");
        }
    }

    private void assertTenantSelfScope(StudentDashboardReadScope scope) {
        String requestedTenantId = blankToNull(scope.requestedTenantId());
        if (requestedTenantId != null && !requestedTenantId.equals(scope.tenantId())) {
            throw new ForbiddenException("GraphQL tenant scope does not match the authenticated principal.");
        }
        String requestedStudentUserId = blankToNull(scope.requestedStudentUserId());
        if (requestedStudentUserId != null && !requestedStudentUserId.equals(scope.userId())) {
            throw new ForbiddenException("GraphQL student dashboard is restricted to the authenticated student.");
        }
    }

    private StudentDashboardReadScope requireScope(StudentDashboardReadScope scope) {
        if (scope == null || blankToNull(scope.userId()) == null || blankToNull(scope.tenantId()) == null) {
            throw new ForbiddenException("GraphQL student dashboard requires an authenticated principal.");
        }
        return scope;
    }
}
