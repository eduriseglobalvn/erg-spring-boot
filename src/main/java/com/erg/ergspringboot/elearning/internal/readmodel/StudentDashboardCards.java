package com.erg.ergspringboot.elearning.internal.readmodel;

import com.erg.ergspringboot.elearning.api.StudentDashboardReadScope;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAnnouncementCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardAssignmentCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardClassMembershipResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardCourseCardResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardNextActionResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardProfileResponse;
import com.erg.ergspringboot.common.response.elearning.StudentDashboardResponses.StudentDashboardScoreSummaryResponse;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollment;
import com.erg.ergspringboot.lms.persistence.projection.AnnouncementCardProjection;
import com.erg.ergspringboot.lms.persistence.projection.StudentDashboardAssignmentProjection;
import com.erg.ergspringboot.lms.persistence.projection.StudentDashboardScoreSummaryProjection;
import com.erg.ergspringboot.elearning.internal.engagement.LeaderboardXp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure projection-to-card mapping and formatting rules for the student dashboard. */
final class StudentDashboardCards {

    private StudentDashboardCards() {
    }

    static List<StudentDashboardCourseCardResponse> toCourseCards(List<StudentDashboardAssignmentProjection> assignments) {
        Map<String, List<StudentDashboardAssignmentProjection>> bySubject = new LinkedHashMap<>();
        for (StudentDashboardAssignmentProjection assignment : assignments) {
            bySubject.computeIfAbsent(courseId(assignment), ignored -> new java.util.ArrayList<>()).add(assignment);
        }
        return bySubject.values().stream()
                .map(StudentDashboardCards::toCourseCard)
                .sorted(Comparator.comparing(StudentDashboardCourseCardResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    static StudentDashboardCourseCardResponse toCourseCard(List<StudentDashboardAssignmentProjection> assignments) {
        StudentDashboardAssignmentProjection first = assignments.getFirst();
        int progressPercent = (int) Math.round(assignments.stream()
                .map(StudentDashboardAssignmentProjection::latestAttemptPercent)
                .filter(percent -> percent != null && percent >= 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D));
        OffsetDateTime updatedAt = assignments.stream()
                .map(StudentDashboardCards::latestUpdate)
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

    static StudentDashboardAssignmentCardResponse toAssignmentCard(StudentDashboardAssignmentProjection projection) {
        return toAssignmentCard(projection, OffsetDateTime.now());
    }

    static StudentDashboardAssignmentCardResponse toAssignmentCard(StudentDashboardAssignmentProjection p, OffsetDateTime now) {
        OffsetDateTime dueAt = p.dueAt();
        boolean handedIn = p.handedInAt() != null;
        boolean pastDue = dueAt != null && now.isAfter(dueAt);
        Integer questions = p.questionCount() != null ? p.questionCount()
                : p.handedInTotalQuestions() != null ? p.handedInTotalQuestions() : p.latestTotalQuestions();
        boolean open = p.latestAttemptStatus() != null
                && !LmsStatePolicy.completedAttemptStates().contains(p.latestAttemptStatus().toLowerCase(java.util.Locale.ROOT));
        Boolean onTime = !handedIn ? null : !Boolean.TRUE.equals(p.handedInLate()) && (dueAt == null || !p.handedInAt().isAfter(dueAt));
        return new StudentDashboardAssignmentCardResponse(
                p.assignmentId(),
                p.quizId(),
                first(p.title(), p.subjectName(), p.subjectId(), "Untitled assignment"),
                assignmentStatus(p),
                dueAt,
                p.handedInPercent(),
                p.handedInAt(),
                p.subjectName(),
                p.teacherName(),
                questions,
                p.maxDurationMinutes() != null && p.maxDurationMinutes() > 0 ? p.maxDurationMinutes() : null,
                "test".equalsIgnoreCase(p.quizKind()) ? "TEST" : "PRACTICE",
                open ? p.latestAnsweredCount() : null,
                p.handedInScore(),
                p.handedInMaxScore(),
                null, // no per-attempt correct count is stored yet
                p.handedInTotalQuestions(),
                p.attemptCount() == null ? 0 : p.attemptCount().intValue(),
                onTime,
                Boolean.TRUE.equals(onTime) && dueAt != null ? Math.max(0, Duration.between(p.handedInAt(), dueAt).toMinutes()) : null,
                handedIn ? 0 : LeaderboardXp.handInXp(questions, !pastDue),
                null, // there is no late-submission window policy; an attempt in progress gets the server grace only
                pastDue
        );
    }

    static StudentDashboardNextActionResponse toNextAction(StudentDashboardAssignmentProjection projection) {
        return new StudentDashboardNextActionResponse(
                projection.assignmentId() + ":next",
                "assignment",
                actionLabel(projection),
                projection.assignmentId(),
                projection.dueAt()
        );
    }

    static StudentDashboardAnnouncementCardResponse toAnnouncementCard(AnnouncementCardProjection projection) {
        return new StudentDashboardAnnouncementCardResponse(
                first(projection.id(), "announcement"),
                first(projection.title(), "Announcement"),
                first(projection.content(), ""),
                Boolean.TRUE.equals(projection.pinned()),
                projection.publishedAt(),
                projection.updatedAt()
        );
    }

    static StudentDashboardProfileResponse toProfile(
            StudentDashboardReadScope scope,
            User user,
            Student student,
            String selectedClassId,
            boolean leaderboardEnabled
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
                student == null ? null : student.getUpdatedAt(),
                leaderboardEnabled
        );
    }

    static StudentDashboardClassMembershipResponse toClassMembership(
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

    static StudentDashboardScoreSummaryResponse toScoreSummary(StudentDashboardScoreSummaryProjection projection) {
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

    static StudentDashboardScoreSummaryResponse emptyScoreSummary() {
        return new StudentDashboardScoreSummaryResponse(0, 0, 0, 0, 0);
    }

    static boolean isPendingAction(StudentDashboardAssignmentProjection projection) {
        return LmsStatePolicy.assignmentIsPendingAction(projection.status(), projection.latestAttemptStatus());
    }

    static String assignmentStatus(StudentDashboardAssignmentProjection projection) {
        return LmsStatePolicy.learnerAssignmentStatus(projection.status(), projection.latestAttemptStatus());
    }

    static String inferCourseStatus(List<StudentDashboardAssignmentProjection> assignments) {
        boolean hasPending = assignments.stream().anyMatch(StudentDashboardCards::isPendingAction);
        if (hasPending) {
            return "active";
        }
        return assignments.stream()
                .map(StudentDashboardAssignmentProjection::status)
                .map(StudentDashboardCards::blankToNull)
                .filter(status -> status != null)
                .findFirst()
                .orElse("active");
    }

    static OffsetDateTime latestUpdate(StudentDashboardAssignmentProjection projection) {
        if (projection.latestAttemptUpdatedAt() != null) {
            return projection.latestAttemptUpdatedAt();
        }
        return projection.assignmentUpdatedAt();
    }

    static String actionLabel(StudentDashboardAssignmentProjection projection) {
        String title = first(projection.title(), projection.subjectName(), projection.subjectId(), "assignment");
        return projection.latestAttemptId() == null ? "Start " + title : "Continue " + title;
    }

    static String courseId(StudentDashboardAssignmentProjection projection) {
        return first(projection.subjectId(), "class:" + projection.academicClassId());
    }

    static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    static int boundedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, value);
    }

    static long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    static int roundPercent(Double percent) {
        return percent == null ? 0 : (int) Math.round(percent);
    }

    static String first(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
