package com.erg.ergspringboot.lms.internal.academicrecord;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.LmsAcademicRecordOperations.AttendanceMark;
import com.erg.ergspringboot.lms.LmsAcademicRecordResponses.AttendanceRecordResponse;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/** Writes the attendance of one class period in two statements (membership check, then one insert-on-conflict) instead of per student. */
final class AttendanceBatchWriter {
    static final int MAX_MARKS = 500;
    private static final Set<String> STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED");

    private final JdbcTemplate jdbc;

    AttendanceBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Throws ForbiddenException unless every student belongs to the class: one query for the whole list. */
    void assertStudentsInClass(String tenantId, String classId, Collection<String> studentIds) {
        List<String> ids = studentIds.stream().distinct().toList();
        if (ids.isEmpty()) return;
        if (ids.size() > MAX_MARKS) throw new BadRequestException("Too many students in one request.");
        List<Object> args = new ArrayList<>(List.of(tenantId));
        args.addAll(ids);
        args.add(classId);
        args.add(classId);
        Integer members = jdbc.queryForObject("""
                select count(*) from lms_students s
                where s.tenant_id = ? and s.id in (%s)
                  and (s.academic_class_id = ?
                       or exists (select 1 from lms_student_enrollments e
                                  where e.tenant_id = s.tenant_id and e.student_id = s.id
                                    and e.class_id = ? and upper(e.status) = 'ENROLLED'))
                """.formatted(marks(ids.size(), "?")), Integer.class, args.toArray());
        if (members == null || members != ids.size()) throw new ForbiddenException("Student does not belong to this class.");
    }

    /** Creates or updates every mark; the version is 1 for a new record and +1 for an existing one, as the single upsert does. */
    List<AttendanceRecordResponse> upsert(String tenantId, String actorUserId, String classId, String subjectId, LocalDate date,
                                          int lessonIndex, List<AttendanceMark> marks, OffsetDateTime now) {
        Map<String, String> statusByStudent = new LinkedHashMap<>();
        for (AttendanceMark mark : marks) {
            String status = mark.status() == null ? "" : mark.status().trim().toUpperCase();
            if (!STATUSES.contains(status)) throw new BadRequestException("Unsupported attendance status.");
            if (mark.studentId() == null || mark.studentId().isBlank() || mark.studentId().length() > 36) {
                throw new BadRequestException("studentId is required.");
            }
            statusByStudent.put(mark.studentId().trim(), status); // a repeated student: the last mark wins
        }
        if (statusByStudent.isEmpty()) return List.of();
        if (statusByStudent.size() > MAX_MARKS) throw new BadRequestException("Too many students in one request.");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(classId);
        args.add(subjectId);
        args.add(date);
        args.add(lessonIndex);
        args.add(actorUserId);
        args.add(now);
        args.add(now);
        statusByStudent.forEach((student, status) -> { args.add(IdGenerator.newId()); args.add(student); args.add(status); });
        return jdbc.query("""
                insert into lms_attendance_records
                  (id, tenant_id, class_id, student_id, subject_id, attendance_date, lesson_index,
                   status, version, updated_by, created_at, updated_at)
                select v.id, cast(? as varchar), cast(? as varchar), v.student_id, cast(? as varchar), cast(? as date),
                       cast(? as integer), v.status, 1, cast(? as varchar), cast(? as timestamptz), cast(? as timestamptz)
                from (values %s) as v(id, student_id, status)
                on conflict (tenant_id, class_id, student_id, subject_id, attendance_date, lesson_index)
                do update set status = excluded.status, version = lms_attendance_records.version + 1,
                              updated_by = excluded.updated_by, updated_at = excluded.updated_at
                returning id, student_id, subject_id, attendance_date, lesson_index, status, version, updated_at
                """.formatted(marks(statusByStudent.size(), "(?, ?, ?)")), (rs, row) -> new AttendanceRecordResponse(
                rs.getString("id"), rs.getString("student_id"), rs.getString("subject_id"),
                rs.getObject("attendance_date", LocalDate.class).toString(), rs.getInt("lesson_index"),
                rs.getString("status"), rs.getInt("version"), rs.getObject("updated_at", OffsetDateTime.class)),
                args.toArray());
    }

    private static String marks(int count, String mark) {
        return String.join(",", Collections.nCopies(count, mark));
    }
}
