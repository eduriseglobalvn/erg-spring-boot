package com.erg.ergspringboot.lms;

import static com.erg.ergspringboot.lms.LmsAcademicRecordResponses.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface LmsAcademicRecordOperations {
    AttendanceWorkspaceResponse attendanceWorkspace(String tenantId, String classId, String subjectId, String from, String to);
    GradebookWorkspaceResponse gradebookWorkspace(String tenantId, String classId, String subjectId);
    /** School of the class, or null when the class does not exist in the tenant. */
    String schoolOfClass(String tenantId, String classId);
    /** Throws ForbiddenException unless the student belongs to the class. */
    void assertStudentInClass(String tenantId, String classId, String studentId);
    AttendanceRecordResponse upsertAttendance(AttendanceCommand command);
    /** Throws ForbiddenException unless every student belongs to the class (one query for the list). */
    void assertStudentsInClass(String tenantId, String classId, Collection<String> studentIds);
    /** Writes the marks of one class period together; each record gets version 1 (new) or +1, like {@link #upsertAttendance}. */
    List<AttendanceRecordResponse> upsertAttendanceBatch(String tenantId, String actorUserId, String classId, String subjectId,
                                                         String attendanceDate, int lessonIndex, List<AttendanceMark> marks);
    GradebookScoreResponse upsertGradebookScore(GradebookScoreCommand command);
    /** Removes one cell's score (the cell becomes "not graded"); false when there was none. */
    boolean deleteGradebookScore(String tenantId, String classId, String subjectId, String studentId, String columnId);

    record AttendanceMark(String studentId, String status) { }

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
