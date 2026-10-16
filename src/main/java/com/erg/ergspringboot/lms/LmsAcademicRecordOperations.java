package com.erg.ergspringboot.lms;

import static com.erg.ergspringboot.lms.LmsAcademicRecordResponses.*;

import java.math.BigDecimal;

public interface LmsAcademicRecordOperations {
    AttendanceWorkspaceResponse attendanceWorkspace(String tenantId, String classId, String subjectId, String from, String to);
    GradebookWorkspaceResponse gradebookWorkspace(String tenantId, String classId, String subjectId);
    AttendanceRecordResponse upsertAttendance(AttendanceCommand command);
    GradebookScoreResponse upsertGradebookScore(GradebookScoreCommand command);

    record AttendanceCommand(String tenantId, String actorUserId, String classId, String studentId,
                             String subjectId, String attendanceDate, int lessonIndex, String status,
                             Integer expectedVersion, String operationId, String idempotencyKey) {
        public AttendanceCommand(String tenantId, String actorUserId, String classId, String studentId,
                                  String subjectId, String attendanceDate, int lessonIndex, String status,
                                  Integer expectedVersion) {
            this(tenantId, actorUserId, classId, studentId, subjectId, attendanceDate, lessonIndex, status,
                    expectedVersion, null, null);
        }
    }
    record GradebookScoreCommand(String tenantId, String actorUserId, String classId, String studentId,
                                 String subjectId, String columnId, String label, BigDecimal maxScore,
                                 String kind, int position, BigDecimal score, Integer expectedVersion,
                                 String operationId, String idempotencyKey) {
        public GradebookScoreCommand(String tenantId, String actorUserId, String classId, String studentId,
                                     String subjectId, String columnId, String label, BigDecimal maxScore,
                                     String kind, int position, BigDecimal score, Integer expectedVersion) {
            this(tenantId, actorUserId, classId, studentId, subjectId, columnId, label, maxScore, kind, position,
                    score, expectedVersion, null, null);
        }
    }
}
