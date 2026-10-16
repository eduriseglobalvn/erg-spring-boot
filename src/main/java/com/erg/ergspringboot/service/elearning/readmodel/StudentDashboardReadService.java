package com.erg.ergspringboot.service.elearning.readmodel;

import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAnnouncementCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardClassMembershipResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardProfileResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAssignmentCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardCourseCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardNextActionResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardScoreSummaryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardSummaryResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardViewerResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentEnrollment;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentEnrollmentRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.projection.AnnouncementCardProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardAssignmentCountProjection;
import com.erg.ergspringboot.repository.lms.projection.StudentDashboardScoreSummaryProjection;
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

/** Explicit persistent dashboard read adapter; callers should use {@code ElearningOperations}. */
@Deprecated(since = "2026-08-08", forRemoval = false)
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
                .map(this::toAssignmentCard)
                .toList();
        List<StudentDashboardNextActionResponse> nextActions = assignmentSlice.stream()
                .filter(this::isPendingAction)
                .limit(5)
                .map(this::toNextAction)
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
                        .map(this::toAnnouncementCard)
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

    private List<StudentDashboardCourseCardResponse> toCourseCards(List<StudentDashboardAssignmentProjection> assignments) {
        Map<String, List<StudentDashboardAssignmentProjection>> bySubject = new LinkedHashMap<>();
        for (StudentDashboardAssignmentProjection assignment : assignments) {
            bySubject.computeIfAbsent(courseId(assignment), ignored -> new java.util.ArrayList<>()).add(assignment);
        }
        return bySubject.values().stream()
                .map(this::toCourseCard)
                .sorted(Comparator.comparing(StudentDashboardCourseCardResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
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

    private StudentDashboardCourseCardResponse toCourseCard(List<StudentDashboardAssignmentProjection> assignments) {
        StudentDashboardAssignmentProjection first = assignments.getFirst();
        int progressPercent = (int) Math.round(assignments.stream()
                .map(StudentDashboardAssignmentProjection::latestAttemptPercent)
                .filter(percent -> percent != null && percent >= 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D));
        OffsetDateTime updatedAt = assignments.stream()
                .map(this::latestUpdate)
                .filter(updated -> updated != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return new StudentDashboardCourseCardResponse(
                courseId(first),
                first(first.subjectName(), first.subjectId(), "Class assignments"),
                inferCourseStatus(assignments),
                clampPercent(progressPercent),
                updatedAt
        );
    }

    private StudentDashboardAssignmentCardResponse toAssignmentCard(StudentDashboardAssignmentProjection projection) {
        return new StudentDashboardAssignmentCardResponse(
                projection.assignmentId(),
                projection.quizId(),
                first(projection.title(), projection.subjectName(), projection.subjectId(), "Untitled assignment"),
                assignmentStatus(projection),
                projection.dueAt()
        );
    }

    private StudentDashboardNextActionResponse toNextAction(StudentDashboardAssignmentProjection projection) {
        return new StudentDashboardNextActionResponse(
                projection.assignmentId() + ":next",
                "assignment",
                actionLabel(projection),
                projection.assignmentId(),
                projection.dueAt()
        );
    }

    private StudentDashboardAnnouncementCardResponse toAnnouncementCard(AnnouncementCardProjection projection) {
        return new StudentDashboardAnnouncementCardResponse(
                first(projection.id(), "announcement"),
                first(projection.title(), "Announcement"),
                first(projection.content(), ""),
                Boolean.TRUE.equals(projection.pinned()),
                projection.publishedAt(),
                projection.updatedAt()
        );
    }

    private StudentDashboardProfileResponse toProfile(
            StudentDashboardReadScope scope,
            User user,
            Student student,
            String selectedClassId
    ) {
        return new StudentDashboardProfileResponse(
                scope.userId(),
                student == null ? null : student.getId(),
                student == null ? null : student.getStudentCode(),
                first(
                        student == null ? null : student.getFullName(),
                        user == null ? null : user.getFullName()
                ),
                first(
                        student == null ? null : student.getEmail(),
                        user == null ? null : user.getEmail()
                ),
                student == null ? null : student.getSchoolId(),
                selectedClassId,
                first(
                        student == null ? null : student.getStatus(),
                        user == null ? null : user.getStatus()
                ),
                student == null ? null : student.getUpdatedAt()
        );
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

    private StudentDashboardClassMembershipResponse toClassMembership(
            Student student,
            String classId,
            StudentEnrollment enrollment,
            Classroom classroom
    ) {
        return new StudentDashboardClassMembershipResponse(
                classId,
                first(classroom == null ? null : classroom.getName(), classId),
                first(
                        classroom == null ? null : classroom.getSchoolId(),
                        enrollment == null ? null : enrollment.getSchoolId(),
                        student.getSchoolId()
                ),
                classroom == null ? null : blankToNull(classroom.getGrade()),
                first(
                        enrollment == null ? null : enrollment.getAcademicYear(),
                        classroom == null ? null : classroom.getAcademicYear()
                ),
                first(enrollment == null ? null : enrollment.getStatus(), "ENROLLED")
        );
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

    private StudentDashboardScoreSummaryResponse toScoreSummary(StudentDashboardScoreSummaryProjection projection) {
        if (projection == null) {
            return emptyScoreSummary();
        }
        return new StudentDashboardScoreSummaryResponse(
                boundedInt(nullToZero(projection.completedAttemptCount())),
                boundedInt(nullToZero(projection.attemptedAssignmentCount())),
                boundedInt(nullToZero(projection.passedAttemptCount())),
                clampPercent(roundPercent(projection.averagePercent())),
                clampPercent(roundPercent(projection.bestPercent()))
        );
    }

    private StudentDashboardScoreSummaryResponse emptyScoreSummary() {
        return new StudentDashboardScoreSummaryResponse(0, 0, 0, 0, 0);
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

    private boolean isPendingAction(StudentDashboardAssignmentProjection projection) {
        if (!"active".equalsIgnoreCase(blankToNull(projection.status()))) {
            return false;
        }
        String attemptStatus = blankToNull(projection.latestAttemptStatus());
        return attemptStatus == null
                || !List.of("submitted", "graded", "completed").contains(attemptStatus.toLowerCase());
    }

    private String assignmentStatus(StudentDashboardAssignmentProjection projection) {
        String attemptStatus = blankToNull(projection.latestAttemptStatus());
        if (attemptStatus != null) {
            return attemptStatus;
        }
        return first(projection.status(), "assigned");
    }

    private String inferCourseStatus(List<StudentDashboardAssignmentProjection> assignments) {
        boolean hasPending = assignments.stream().anyMatch(this::isPendingAction);
        if (hasPending) {
            return "active";
        }
        return assignments.stream()
                .map(StudentDashboardAssignmentProjection::status)
                .map(this::blankToNull)
                .filter(status -> status != null)
                .findFirst()
                .orElse("active");
    }

    private OffsetDateTime latestUpdate(StudentDashboardAssignmentProjection projection) {
        if (projection.latestAttemptUpdatedAt() != null) {
            return projection.latestAttemptUpdatedAt();
        }
        return projection.assignmentUpdatedAt();
    }

    private String actionLabel(StudentDashboardAssignmentProjection projection) {
        String title = first(projection.title(), projection.subjectName(), projection.subjectId(), "assignment");
        return projection.latestAttemptId() == null ? "Start " + title : "Continue " + title;
    }

    private String courseId(StudentDashboardAssignmentProjection projection) {
        return first(projection.subjectId(), "class:" + projection.academicClassId());
    }

    private int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private int boundedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, value);
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private int roundPercent(Double percent) {
        return percent == null ? 0 : (int) Math.round(percent);
    }

    private StudentDashboardReadScope requireScope(StudentDashboardReadScope scope) {
        if (scope == null || blankToNull(scope.userId()) == null || blankToNull(scope.tenantId()) == null) {
            throw new ForbiddenException("GraphQL student dashboard requires an authenticated principal.");
        }
        return scope;
    }

    private String first(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
