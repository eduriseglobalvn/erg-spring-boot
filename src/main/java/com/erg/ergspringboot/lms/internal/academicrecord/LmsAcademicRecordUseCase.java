package com.erg.ergspringboot.lms.internal.academicrecord;

import static com.erg.ergspringboot.lms.LmsAcademicRecordResponses.*;
import static com.erg.ergspringboot.lms.LmsAcademicRecordOperations.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.SyncConflictException;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations;
import com.erg.ergspringboot.common.response.sync.SyncMutationResponses.ConflictPayload;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.utils.IdGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsAcademicRecordUseCase implements LmsAcademicRecordOperations {
    private static final Set<String> ATTENDANCE_STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED");
    private static final Set<String> GRADE_KINDS = Set.of("TOPIC", "QUIZ", "OFFLINE");

    private final JdbcTemplate jdbc;
    private final LmsSyncOperationService sync;

    public LmsAcademicRecordUseCase(JdbcTemplate jdbc) {
        this(jdbc, (LmsSyncOperationService) null);
    }

    @Autowired
    public LmsAcademicRecordUseCase(JdbcTemplate jdbc, ObjectProvider<LmsSyncOperationService> sync) {
        this(jdbc, sync == null ? null : sync.getIfAvailable());
    }

    private LmsAcademicRecordUseCase(JdbcTemplate jdbc, LmsSyncOperationService sync) {
        this.jdbc = jdbc;
        this.sync = sync;
    }

    @Transactional(readOnly = true)
    public AttendanceWorkspaceResponse attendanceWorkspace(String tenantId, String classId, String subjectId,
                                                            String fromValue, String toValue) {
        LocalDate from = date(fromValue, "from");
        LocalDate to = date(toValue, "to");
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 93) {
            throw new BadRequestException("Attendance range must be ordered and no longer than 93 days.");
        }
        List<AttendanceRecordResponse> records = jdbc.query("""
                select id, student_id, subject_id, attendance_date, lesson_index, status, version, updated_at
                from lms_attendance_records
                where tenant_id = ? and class_id = ? and subject_id = ? and attendance_date between ? and ?
                order by attendance_date, lesson_index, student_id
                """, (rs, row) -> new AttendanceRecordResponse(
                rs.getString("id"), rs.getString("student_id"), rs.getString("subject_id"),
                rs.getObject("attendance_date", LocalDate.class).toString(), rs.getInt("lesson_index"),
                rs.getString("status"), rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)
        ), tenantId, required(classId, "classId", 36), required(subjectId, "subjectId", 100), from, to);
        return new AttendanceWorkspaceResponse(tenantId, classId, subjectId, from.toString(), to.toString(), records);
    }

    @Transactional(readOnly = true)
    public GradebookWorkspaceResponse gradebookWorkspace(String tenantId, String classId, String subjectId) {
        required(classId, "classId", 36);
        required(subjectId, "subjectId", 100);
        List<GradebookColumnResponse> columns = jdbc.query("""
                select id, label, max_score, kind, position, version, updated_at
                from lms_gradebook_columns where tenant_id = ? and class_id = ? and subject_id = ?
                order by position, id
                """, (rs, row) -> new GradebookColumnResponse(
                rs.getString("id"), rs.getString("label"), rs.getBigDecimal("max_score"),
                rs.getString("kind"), rs.getInt("position"), rs.getInt("version"),
                rs.getObject("updated_at", OffsetDateTime.class)
        ), tenantId, classId, subjectId);
        List<GradebookScoreResponse> scores = jdbc.query("""
                select student_id, column_id, score, max_score, version, updated_at
                from lms_gradebook_scores where tenant_id = ? and class_id = ? and subject_id = ?
                order by student_id, column_id
                """, (rs, row) -> new GradebookScoreResponse(
                rs.getString("student_id"), rs.getString("column_id"), rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"), rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)
        ), tenantId, classId, subjectId);
        return new GradebookWorkspaceResponse(tenantId, classId, subjectId, columns, scores);
    }

    @Transactional
    public AttendanceRecordResponse upsertAttendance(AttendanceCommand command) {
        String status = required(command.status(), "status", 16).toUpperCase();
        if (!ATTENDANCE_STATUSES.contains(status)) throw new BadRequestException("Unsupported attendance status.");
        LocalDate attendanceDate = date(command.attendanceDate(), "attendanceDate");
        if (command.lessonIndex() < 1 || command.lessonIndex() > 20) throw new BadRequestException("lessonIndex must be between 1 and 20.");
        String entityId = required(command.classId(), "classId", 36) + ":" + required(command.studentId(), "studentId", 36)
                + ":" + required(command.subjectId(), "subjectId", 100) + ":" + attendanceDate + ":" + command.lessonIndex();
        LmsSyncOperationService.Receipt receipt = reserve(command.tenantId(), command.actorUserId(), command.operationId(),
                command.idempotencyKey(), "lms.attendance", entityId, command,
                command.expectedVersion() == null ? null : command.expectedVersion().longValue());
        if (receipt != null && receipt.replayed()) {
            return sync.result(receipt, AttendanceRecordResponse.class);
        }
        List<AttendanceRecordResponse> existing = attendanceByNaturalKey(command, attendanceDate);
        OffsetDateTime now = serverTime();
        try {
            if (existing.isEmpty()) {
                if (command.expectedVersion() != null && command.expectedVersion() != 0) throw stale(
                        command, "lms.attendance", entityId, 0L);
                jdbc.update("""
                    insert into lms_attendance_records
                      (id, tenant_id, class_id, student_id, subject_id, attendance_date, lesson_index,
                       status, version, updated_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                        """, IdGenerator.newId(), command.tenantId(), command.classId(), command.studentId(),
                        command.subjectId(), attendanceDate, command.lessonIndex(), status, command.actorUserId(), now, now);
            } else {
                AttendanceRecordResponse current = existing.getFirst();
                if (command.expectedVersion() != null && command.expectedVersion() != current.version()) throw stale(
                        command, "lms.attendance", entityId, (long) current.version());
                jdbc.update("""
                    update lms_attendance_records set status = ?, version = version + 1, updated_by = ?, updated_at = ?
                    where tenant_id = ? and id = ?
                        """, status, command.actorUserId(), now, command.tenantId(), current.id());
            }
            AttendanceRecordResponse result = attendanceByNaturalKey(command, attendanceDate).getFirst();
            complete(receipt, result, result.version());
            return result;
        } catch (RuntimeException exception) {
            abandon(receipt);
            throw exception;
        }
    }

    @Transactional
    public GradebookScoreResponse upsertGradebookScore(GradebookScoreCommand command) {
        String kind = required(command.kind(), "kind", 16).toUpperCase();
        if (!GRADE_KINDS.contains(kind)) throw new BadRequestException("Unsupported gradebook column kind.");
        if (command.score() == null || command.maxScore() == null || command.maxScore().signum() <= 0
                || command.score().signum() < 0 || command.score().compareTo(command.maxScore()) > 0) {
            throw new BadRequestException("Score must be between zero and maxScore.");
        }
        String entityId = required(command.classId(), "classId", 36) + ":" + required(command.studentId(), "studentId", 36)
                + ":" + required(command.subjectId(), "subjectId", 100) + ":" + required(command.columnId(), "columnId", 100);
        LmsSyncOperationService.Receipt receipt = reserve(command.tenantId(), command.actorUserId(), command.operationId(),
                command.idempotencyKey(), "lms.gradebook", entityId, command,
                command.expectedVersion() == null ? null : command.expectedVersion().longValue());
        if (receipt != null && receipt.replayed()) {
            return sync.result(receipt, GradebookScoreResponse.class);
        }
        OffsetDateTime now = serverTime();
        try {
            jdbc.update("""
                insert into lms_gradebook_columns
                  (tenant_id, class_id, subject_id, id, label, max_score, kind, position, version, updated_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                on conflict (tenant_id, class_id, subject_id, id) do update
                  set label = excluded.label, max_score = excluded.max_score, kind = excluded.kind,
                      position = excluded.position, version = lms_gradebook_columns.version + 1,
                      updated_by = excluded.updated_by, updated_at = excluded.updated_at
                """, command.tenantId(), command.classId(), command.subjectId(), command.columnId(),
                required(command.label(), "label", 160), command.maxScore(), kind, Math.max(0, command.position()),
                    command.actorUserId(), now, now);
            List<GradebookScoreResponse> existing = gradeByNaturalKey(command);
            if (existing.isEmpty()) {
                if (command.expectedVersion() != null && command.expectedVersion() != 0) throw stale(
                        command, "lms.gradebook", entityId, 0L);
                jdbc.update("""
                    insert into lms_gradebook_scores
                      (tenant_id, class_id, subject_id, student_id, column_id, score, max_score,
                       version, updated_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                        """, command.tenantId(), command.classId(), command.subjectId(), command.studentId(),
                        command.columnId(), command.score(), command.maxScore(), command.actorUserId(), now, now);
            } else {
                GradebookScoreResponse current = existing.getFirst();
                if (command.expectedVersion() != null && command.expectedVersion() != current.version()) throw stale(
                        command, "lms.gradebook", entityId, (long) current.version());
                jdbc.update("""
                    update lms_gradebook_scores set score = ?, max_score = ?, version = version + 1,
                      updated_by = ?, updated_at = ?
                    where tenant_id = ? and class_id = ? and subject_id = ? and student_id = ? and column_id = ?
                        """, command.score(), command.maxScore(), command.actorUserId(), now, command.tenantId(),
                        command.classId(), command.subjectId(), command.studentId(), command.columnId());
            }
            GradebookScoreResponse result = gradeByNaturalKey(command).getFirst();
            complete(receipt, result, result.version());
            return result;
        } catch (RuntimeException exception) {
            abandon(receipt);
            throw exception;
        }
    }

    private List<AttendanceRecordResponse> attendanceByNaturalKey(AttendanceCommand command, LocalDate date) {
        return jdbc.query("""
                select id, student_id, subject_id, attendance_date, lesson_index, status, version, updated_at
                from lms_attendance_records where tenant_id = ? and class_id = ? and student_id = ?
                  and subject_id = ? and attendance_date = ? and lesson_index = ?
                """, (rs, row) -> new AttendanceRecordResponse(
                rs.getString("id"), rs.getString("student_id"), rs.getString("subject_id"),
                rs.getObject("attendance_date", LocalDate.class).toString(), rs.getInt("lesson_index"),
                rs.getString("status"), rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)
        ), command.tenantId(), required(command.classId(), "classId", 36), required(command.studentId(), "studentId", 36),
                required(command.subjectId(), "subjectId", 100), date, command.lessonIndex());
    }

    private List<GradebookScoreResponse> gradeByNaturalKey(GradebookScoreCommand command) {
        return jdbc.query("""
                select student_id, column_id, score, max_score, version, updated_at from lms_gradebook_scores
                where tenant_id = ? and class_id = ? and subject_id = ? and student_id = ? and column_id = ?
                """, (rs, row) -> new GradebookScoreResponse(
                rs.getString("student_id"), rs.getString("column_id"), rs.getBigDecimal("score"),
                rs.getBigDecimal("max_score"), rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)
        ), command.tenantId(), required(command.classId(), "classId", 36), required(command.subjectId(), "subjectId", 100),
                required(command.studentId(), "studentId", 36), required(command.columnId(), "columnId", 100));
    }

    private LocalDate date(String value, String field) {
        try { return LocalDate.parse(required(value, field, 10)); }
        catch (java.time.format.DateTimeParseException exception) { throw new BadRequestException(field + " must use yyyy-MM-dd."); }
    }

    private String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new BadRequestException(field + " is required.");
        String normalized = value.trim();
        if (normalized.length() > max) throw new BadRequestException(field + " exceeds " + max + " characters.");
        return normalized;
    }

    private SyncConflictException stale(AttendanceCommand command, String entityType, String entityId, long actual) {
        return new SyncConflictException(new ConflictPayload("STALE_WRITE", command.operationId(), command.idempotencyKey(),
                entityType, entityId, command.expectedVersion() == null ? null : command.expectedVersion().longValue(),
                actual, serverTime(), true), "Academic record has been updated by another request.");
    }

    private SyncConflictException stale(GradebookScoreCommand command, String entityType, String entityId, long actual) {
        return new SyncConflictException(new ConflictPayload("STALE_WRITE", command.operationId(), command.idempotencyKey(),
                entityType, entityId, command.expectedVersion() == null ? null : command.expectedVersion().longValue(),
                actual, serverTime(), true), "Academic record has been updated by another request.");
    }

    private LmsSyncOperationService.Receipt reserve(String tenant, String account, String operationId, String key,
                                                     String entityType, String entityId, Object payload, Long expected) {
        return sync == null ? null : sync.reserve(tenant, account, operationId, key, entityType, entityId, payload, expected).orElse(null);
    }

    private void complete(LmsSyncOperationService.Receipt receipt, Object result, long version) {
        if (receipt != null) sync.complete(receipt, result, version);
    }

    private void abandon(LmsSyncOperationService.Receipt receipt) {
        if (receipt != null) sync.abandon(receipt);
    }

    private OffsetDateTime serverTime() { return sync == null ? OffsetDateTime.now() : sync.serverTime(); }

}
