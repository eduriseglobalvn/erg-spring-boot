package com.erg.ergspringboot.lms.internal.academicrecord;

import static com.erg.ergspringboot.lms.LmsAcademicRecordResponses.*;

import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.AttendanceCommand;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.GradebookScoreCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class LmsAcademicRecordGraphQlController {
    private final LmsAcademicRecordOperations records;
    private final GraphQlAccessSupport access;

    public LmsAcademicRecordGraphQlController(LmsAcademicRecordOperations records, GraphQlAccessSupport access) {
        this.records = records;
        this.access = access;
    }

    @SchemaMapping(typeName = "LmsQuery", field = "attendanceWorkspace")
    public AttendanceWorkspaceResponse attendanceWorkspace(@Argument AttendanceWorkspaceInput input) {
        ErgPrincipal principal = principal("lms.attendance.read", "lms.manage", "*");
        access.assertClassScope(principal, input.classId(), input.schoolId());
        String tenantId = access.verifiedTenantId(principal, input.tenantId());
        String to = input.to() == null ? LocalDate.now().toString() : input.to();
        String from = input.from() == null ? LocalDate.parse(to).minusDays(31).toString() : input.from();
        return records.attendanceWorkspace(tenantId, input.classId(), input.subjectId(), from, to);
    }

    @SchemaMapping(typeName = "LmsQuery", field = "gradebookWorkspace")
    public GradebookWorkspaceResponse gradebookWorkspace(@Argument GradebookWorkspaceInput input) {
        ErgPrincipal principal = principal("lms.grade.read", "lms.manage", "*");
        access.assertClassScope(principal, input.classId(), input.schoolId());
        return records.gradebookWorkspace(access.verifiedTenantId(principal, input.tenantId()), input.classId(), input.subjectId());
    }

    @SchemaMapping(typeName = "Mutation", field = "upsertAttendance")
    public AttendanceRecordResponse upsertAttendance(@Argument UpsertAttendanceInput input) {
        ErgPrincipal principal = principal("lms.attendance.write", "lms.manage", "*");
        access.assertClassScope(principal, input.classId(), input.schoolId());
        return records.upsertAttendance(new AttendanceCommand(
                access.verifiedTenantId(principal, input.tenantId()), principal.userId(), input.classId(),
                input.studentId(), input.subjectId(), input.attendanceDate(), input.lessonIndex(),
                input.status(), input.expectedVersion(), input.operationId(), input.idempotencyKey()));
    }

    @SchemaMapping(typeName = "Mutation", field = "upsertGradebookScore")
    public GradebookScoreResponse upsertGradebookScore(@Argument UpsertGradebookScoreInput input) {
        ErgPrincipal principal = principal("lms.grade.write", "lms.manage", "*");
        access.assertClassScope(principal, input.classId(), input.schoolId());
        return records.upsertGradebookScore(new GradebookScoreCommand(
                access.verifiedTenantId(principal, input.tenantId()), principal.userId(), input.classId(),
                input.studentId(), input.subjectId(), input.columnId(), input.label(), input.maxScore(),
                input.kind(), input.position() == null ? 0 : input.position(), input.score(), input.expectedVersion(),
                input.operationId(), input.idempotencyKey()));
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
