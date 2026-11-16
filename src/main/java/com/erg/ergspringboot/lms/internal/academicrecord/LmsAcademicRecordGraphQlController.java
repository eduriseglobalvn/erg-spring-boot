package com.erg.ergspringboot.lms.internal.academicrecord;

import static com.erg.ergspringboot.lms.LmsAcademicRecordResponses.*;

import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.AttendanceCommand;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.GradebookScoreCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class LmsAcademicRecordGraphQlController {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final LmsAcademicRecordOperations records;
    private final GraphQlAccessSupport access;

    public LmsAcademicRecordGraphQlController(LmsAcademicRecordOperations records, GraphQlAccessSupport access) {
        this.records = records;
        this.access = access;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "attendanceWorkspace")
    public AttendanceWorkspaceResponse attendanceWorkspace(@Argument AttendanceWorkspaceInput input) {
        ErgPrincipal principal = principal("lms.attendance.read", "lms.manage", "*");
        String tenantId = access.verifiedTenantId(principal, input.tenantId());
        assertClassScope(principal, tenantId, input.classId());
        String to = input.to() == null ? LocalDate.now(VIETNAM_ZONE).toString() : input.to();
        String from = input.from() == null ? LocalDate.parse(to).minusDays(31).toString() : input.from();
        return records.attendanceWorkspace(tenantId, input.classId(), input.subjectId(), from, to);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "gradebookWorkspace")
    public GradebookWorkspaceResponse gradebookWorkspace(@Argument GradebookWorkspaceInput input) {
        ErgPrincipal principal = principal("lms.grade.read", "lms.manage", "*");
        String tenantId = access.verifiedTenantId(principal, input.tenantId());
        assertClassScope(principal, tenantId, input.classId());
        return records.gradebookWorkspace(tenantId, input.classId(), input.subjectId());
    }

    @SchemaMapping(typeName = "Mutation", field = "upsertAttendance")
    public AttendanceRecordResponse upsertAttendance(@Argument UpsertAttendanceInput input) {
        ErgPrincipal principal = principal("lms.attendance.write", "lms.manage", "*");
        String tenantId = access.verifiedTenantId(principal, input.tenantId());
        assertClassScope(principal, tenantId, input.classId());
        records.assertStudentInClass(tenantId, input.classId(), input.studentId());
        return records.upsertAttendance(new AttendanceCommand(
                tenantId, principal.userId(), input.classId(),
                input.studentId(), input.subjectId(), input.attendanceDate(), input.lessonIndex(),
                input.status(), input.expectedVersion(), input.operationId(), input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "upsertGradebookScore")
    public GradebookScoreResponse upsertGradebookScore(@Argument UpsertGradebookScoreInput input) {
        ErgPrincipal principal = principal("lms.grade.write", "lms.manage", "*");
        String tenantId = access.verifiedTenantId(principal, input.tenantId());
        assertClassScope(principal, tenantId, input.classId());
        records.assertStudentInClass(tenantId, input.classId(), input.studentId());
        return records.upsertGradebookScore(new GradebookScoreCommand(
                tenantId, principal.userId(), input.classId(),
                input.studentId(), input.subjectId(), input.columnId(), input.label(), input.maxScore(),
                input.kind(), input.position() == null ? 0 : input.position(), input.score(), input.expectedVersion(),
                input.operationId(), input.idempotencyKey()));
    }

    /** The school comes from the class row, never from the client's {@code schoolId}. */
    private void assertClassScope(ErgPrincipal principal, String tenantId, String classId) {
        access.assertClassScope(principal, classId, records.schoolOfClass(tenantId, classId));
    }

    private ErgPrincipal principal(String... permissions) {
        ErgPrincipal principal = access.currentPrincipal();
        access.assertPermission(principal, permissions);
        return principal;
    }

    public record AttendanceWorkspaceInput(String tenantId, String classId, String schoolId, String subjectId,
                                           String from, String to) { }
    public record GradebookWorkspaceInput(String tenantId, String classId, String schoolId, String subjectId) { }
    public record UpsertAttendanceInput(String tenantId, String classId, String schoolId, String studentId,
                                        String subjectId, String attendanceDate, int lessonIndex, String status,
                                        Integer expectedVersion, String operationId, String idempotencyKey) {
        public UpsertAttendanceInput(String tenantId, String classId, String schoolId, String studentId,
                                     String subjectId, String attendanceDate, int lessonIndex, String status,
                                     Integer expectedVersion) {
            this(tenantId, classId, schoolId, studentId, subjectId, attendanceDate, lessonIndex, status,
                    expectedVersion, null, null);
        }
    }
    public record UpsertGradebookScoreInput(String tenantId, String classId, String schoolId, String studentId,
                                            String subjectId, String columnId, String label, BigDecimal maxScore,
                                            String kind, Integer position, BigDecimal score, Integer expectedVersion,
                                            String operationId, String idempotencyKey) {
        public UpsertGradebookScoreInput(String tenantId, String classId, String schoolId, String studentId,
                                         String subjectId, String columnId, String label, BigDecimal maxScore,
                                         String kind, Integer position, BigDecimal score, Integer expectedVersion) {
            this(tenantId, classId, schoolId, studentId, subjectId, columnId, label, maxScore, kind, position,
                    score, expectedVersion, null, null);
        }
    }
}
