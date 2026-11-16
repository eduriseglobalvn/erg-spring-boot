package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.lms.internal.classlog.LessonLogCommandUseCase.AttendanceMark;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.CurriculumLessonRow;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogChange;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogPeriod;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogStats;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonLogWeek;
import com.erg.ergspringboot.lms.internal.classlog.LessonLogDtos.LessonReportMatrix;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Class-log (so dau bai) GraphQL entry points. Reads and writes of the log and the per-period attendance are live
 * (CBG B3-B5); the report matrix and the reminder are CBG B7.
 */
@Controller
public class LessonLogGraphQlController {

    private static final String[] READ = {"lms.class_log.read", "lms.manage", "*"};
    private static final String[] WRITE = {"lms.class_log.write", "lms.manage", "*"};
    private static final String[] REPORT = {"timekeeping.approve", "lms.manage", "*"};

    private final LessonLogQueryUseCase queries;
    private final LessonLogCommandUseCase commands;
    private final LessonReportService reports;
    private final GraphQlAccessSupport access;

    public LessonLogGraphQlController(LessonLogQueryUseCase queries, LessonLogCommandUseCase commands, LessonReportService reports,
                                     GraphQlAccessSupport access) {
        this.queries = queries;
        this.commands = commands;
        this.reports = reports;
        this.access = access;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "lessonLogTeacherWeek")
    public LessonLogWeek lessonLogTeacherWeek(@Argument String weekStart, @Argument String session, @Argument String status) {
        ErgPrincipal principal = principal(READ);
        return queries.teacherWeek(tenant(principal), principal.userId(), weekStart, session, status);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "lessonLogClassWeek")
    public LessonLogWeek lessonLogClassWeek(@Argument String classId, @Argument String weekStart) {
        ErgPrincipal principal = principal(READ);
        String tenantId = tenant(principal);
        access.assertClassScope(principal, classId, queries.schoolOf(tenantId, classId));
        return queries.classWeek(tenantId, classId, weekStart);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "lessonLogChanges")
    public List<LessonLogChange> lessonLogChanges(@Argument String weekStart, @Argument String classId) {
        ErgPrincipal principal = principal(READ);
        String tenantId = tenant(principal);
        if (classId != null) access.assertClassScope(principal, classId, queries.schoolOf(tenantId, classId));
        return queries.changes(tenantId, principal.userId(), weekStart, classId);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "lessonLogCurriculum")
    public List<CurriculumLessonRow> lessonLogCurriculum(@Argument String classId, @Argument String subjectId) {
        ErgPrincipal principal = principal(READ);
        String tenantId = tenant(principal);
        access.assertClassScope(principal, classId, queries.schoolOf(tenantId, classId));
        return queries.curriculum(tenantId, classId, subjectId);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "teacherDayPeriods")
    public List<LessonLogPeriod> teacherDayPeriods(@Argument String date) {
        ErgPrincipal principal = principal(READ);
        return queries.teacherDayPeriods(tenant(principal), principal.userId(), date);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "lessonReportMatrix")
    public LessonReportMatrix lessonReportMatrix(@Argument String weekStart, @Argument String schoolId, @Argument String subjectId) {
        ErgPrincipal principal = principal(REPORT);
        if (schoolId != null) access.assertEducationUnitScope(principal, schoolId);
        return reports.matrix(tenant(principal), weekStart, schoolId, subjectId, access.restrictedEducationUnitIds(principal));
    }

    @MutationMapping
    public LessonLogPeriod updateLessonLogEntry(@Argument UpdateLessonLogEntryInput input) {
        ErgPrincipal principal = principal(WRITE);
        return commands.update(tenant(principal), principal.userId(), input.eventId(), input.date(), input.periodNo(),
                input.actualPeriod(), input.comment(), input.expectedVersion());
    }

    @MutationMapping
    public LessonLogStats confirmLessonLogWeek(@Argument String weekStart) {
        ErgPrincipal principal = principal(WRITE);
        return commands.confirmWeek(tenant(principal), principal.userId(), weekStart);
    }

    @MutationMapping
    public LessonLogPeriod saveAttendanceAndLog(@Argument SaveAttendanceAndLogInput input) {
        ErgPrincipal principal = principal(WRITE);
        access.assertPermission(principal, "lms.attendance.write", "lms.manage", "*");
        List<AttendanceMark> marks = input.records().stream().map(r -> new AttendanceMark(r.studentId(), r.status())).toList();
        return commands.saveAttendanceAndLog(tenant(principal), principal.userId(), input.eventId(), input.date(),
                input.periodNo(), marks, input.actualPeriod(), input.comment(), input.expectedVersion());
    }

    @MutationMapping
    public int remindLessonReport(@Argument List<String> teacherIds) {
        ErgPrincipal principal = principal(REPORT);
        return reports.remind(tenant(principal), principal.userId(), teacherIds, access.restrictedEducationUnitIds(principal));
    }

    private ErgPrincipal principal(String... permissions) {
        ErgPrincipal principal = access.currentPrincipal();
        access.assertPermission(principal, permissions);
        return principal;
    }

    private String tenant(ErgPrincipal principal) {
        return access.verifiedTenantId(principal, null);
    }

    public record UpdateLessonLogEntryInput(String eventId, String date, int periodNo, int actualPeriod, String comment,
                                            Integer expectedVersion) {}

    public record LessonLogAttendanceInput(String studentId, String status) {}

    public record SaveAttendanceAndLogInput(String eventId, String date, int periodNo, List<LessonLogAttendanceInput> records,
                                            int actualPeriod, String comment, Integer expectedVersion) {}
}
