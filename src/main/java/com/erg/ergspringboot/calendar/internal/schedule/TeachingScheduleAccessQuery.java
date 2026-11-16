package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeacherShortName;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.AccessChangeResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.AccessClassChangeResponse;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The 2-week access rule of KE_HOACH_LICH_DAY.md 2.4 for the preview side: a teacher (main or assistant) keeps a
 * school / class while one of their PUBLISHED events there ends on or after Monday 00:00 (Vietnam) of the week
 * before last. The live gates (ClassroomRepository natives used by login scope and class lists) apply the same
 * threshold; this class only explains the change a draft would make and what is about to lapse.
 */
@Service
public class TeachingScheduleAccessQuery {

    static final int LOOKBACK_DAYS = 14;
    static final int REVOKE_AFTER_DAYS = 21;
    /** A teacher row warns about a lapsing school this many days ahead (B8 revokeNotice). */
    static final int NOTICE_DAYS = 14;

    /** Newest PUBLISHED event of a teacher in one class; the school's last event is the max over its classes. */
    record Held(String teacherId, String schoolId, String classId, String className, OffsetDateTime lastEnd) {
        boolean ended(OffsetDateTime now) {
            return lastEnd.isBefore(now);
        }

        LocalDate revokeOn() {
            return ScheduleConflictEngine.weekStart(lastEnd.atZoneSameInstant(ScheduleDataLoader.VN).toLocalDate())
                    .plusDays(REVOKE_AFTER_DAYS);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    public TeachingScheduleAccessQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Events ending before this instant no longer keep access. */
    static OffsetDateTime since(OffsetDateTime now) {
        return ScheduleConflictEngine.weekStart(now.atZoneSameInstant(ScheduleDataLoader.VN).toLocalDate())
                .minusDays(LOOKBACK_DAYS).atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime();
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    List<Held> held(String tenantId, Collection<String> teacherIds, OffsetDateTime now) {
        if (teacherIds.isEmpty()) return List.of();
        return jdbc.query("""
                select t.teacher_user_id, e.school_id, e.academic_class_id, c.name class_name, max(e.end_at) last_end
                from teaching_schedule_events e
                join teaching_schedule_event_teachers t on t.event_id = e.id
                left join academic_classes c on c.id = e.academic_class_id
                where e.tenant_id = :tenant and e.status = 'PUBLISHED' and e.end_at >= :since
                  and t.teacher_user_id in (:teachers)
                group by t.teacher_user_id, e.school_id, e.academic_class_id, c.name
                """, new MapSqlParameterSource("tenant", tenantId).addValue("since", since(now))
                .addValue("teachers", teacherIds), (rs, i) -> new Held(rs.getString("teacher_user_id"),
                rs.getString("school_id"), rs.getString("academic_class_id"), rs.getString("class_name"),
                rs.getObject("last_end", OffsetDateTime.class)));
    }

    /**
     * What publishing {@code draft} (occurrences of one school) does to each teacher's access: classes and school
     * they already hold are EXISTING, new ones NEW, and classes of the school they only hold through finished
     * events REVOKE with the day access ends.
     */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public List<AccessChangeResponse> accessChanges(String tenantId, String schoolId, List<Occurrence> draft,
            OffsetDateTime now) {
        Map<String, TeacherRef> teachers = new LinkedHashMap<>();
        draft.forEach(o -> o.teachers().forEach(t -> teachers.putIfAbsent(t.id(), t)));
        if (teachers.isEmpty()) return List.of();
        Map<String, List<Held>> heldBy = held(tenantId, teachers.keySet(), now).stream()
                .filter(h -> schoolId.equals(h.schoolId())).collect(Collectors.groupingBy(Held::teacherId));
        List<AccessChangeResponse> result = new ArrayList<>();
        for (TeacherRef teacher : teachers.values()) {
            List<Occurrence> mine = draft.stream().filter(o -> o.hasTeacher(teacher.id())).toList();
            List<Held> held = heldBy.getOrDefault(teacher.id(), List.of());
            Set<String> heldClasses = held.stream().map(Held::classId).collect(Collectors.toSet());
            Map<String, String> draftClasses = new LinkedHashMap<>();
            mine.stream().sorted(Comparator.comparing(o -> ScheduleConflictEngine.nz(o.className())))
                    .forEach(o -> draftClasses.putIfAbsent(o.classId(), o.className()));
            List<AccessClassChangeResponse> classes = new ArrayList<>();
            draftClasses.forEach((id, name) -> classes.add(new AccessClassChangeResponse(id, name,
                    heldClasses.contains(id) ? "EXISTING" : "NEW", null)));
            held.stream().filter(h -> !draftClasses.containsKey(h.classId()) && h.ended(now))
                    .sorted(Comparator.comparing(h -> ScheduleConflictEngine.nz(h.className())))
                    .forEach(h -> classes.add(new AccessClassChangeResponse(h.classId(), ScheduleConflictEngine.nz(h.className()),
                            "REVOKE", h.revokeOn().toString())));
            int weekly = (int) mine.stream().map(Occurrence::rowKey).distinct().count();
            LocalDate last = mine.stream().map(Occurrence::date).max(Comparator.naturalOrder()).orElse(null);
            result.add(new AccessChangeResponse(teacher.id(), TeacherShortName.of(teacher.name()), weekly,
                    last == null ? null : last.toString(), schoolId, held.isEmpty() ? "NEW" : "EXISTING", null, classes));
        }
        result.sort(Comparator.comparing(AccessChangeResponse::teacherName));
        return result;
    }

    /**
     * For each teacher whose access to a school ends within {@link #NOTICE_DAYS} days (no event left in it): the
     * school with the earliest end date and its lapsing classes. Teachers with nothing lapsing are absent.
     */
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public Map<String, AccessChangeResponse> revokeNotices(String tenantId, Map<String, String> teacherNames,
            Map<String, Integer> weeklyPeriods, OffsetDateTime now) {
        LocalDate horizon = now.atZoneSameInstant(ScheduleDataLoader.VN).toLocalDate().plusDays(NOTICE_DAYS);
        Map<String, Map<String, List<Held>>> bySchool = new HashMap<>();
        for (Held h : held(tenantId, teacherNames.keySet(), now)) {
            bySchool.computeIfAbsent(h.teacherId(), k -> new HashMap<>()).computeIfAbsent(h.schoolId(), k -> new ArrayList<>()).add(h);
        }
        Map<String, AccessChangeResponse> result = new HashMap<>();
        bySchool.forEach((teacherId, schools) -> schools.values().stream()
                .filter(classes -> classes.stream().allMatch(h -> h.ended(now)))
                .map(classes -> classes.stream().max(Comparator.comparing(Held::lastEnd)).orElseThrow())
                .filter(last -> !last.revokeOn().isAfter(horizon))
                .min(Comparator.comparing(Held::revokeOn))
                .ifPresent(last -> {
                    List<AccessClassChangeResponse> classes = schools.get(last.schoolId()).stream()
                            .map(h -> new AccessClassChangeResponse(h.classId(), ScheduleConflictEngine.nz(h.className()),
                                    "REVOKE", h.revokeOn().toString()))
                            .sorted(Comparator.comparing(AccessClassChangeResponse::className)).toList();
                    result.put(teacherId, new AccessChangeResponse(teacherId, teacherNames.get(teacherId),
                            weeklyPeriods.getOrDefault(teacherId, 0), last.lastEnd().atZoneSameInstant(ScheduleDataLoader.VN)
                            .toLocalDate().toString(), last.schoolId(), "REVOKE", last.revokeOn().toString(), classes));
                }));
        return result;
    }
}
