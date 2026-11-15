package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeacherShortName;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LeaveInfoResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotClassOption;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotOptionsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotRoomOption;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotTeacherOption;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TravelGapResponse;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodSlot;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.ClassRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Leave;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.RoomRef;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What can go into one grid cell (school, date, period): which classes, teachers and rooms are free, busy, too far
 * away or on leave, with the reason (KE_HOACH_LICH_DAY.md B6). Everything is computed in memory from one query of the
 * tenant's events of the week and the week before.
 */
@Service
public class ScheduleSlotOptionsService {

    private static final Map<String, Integer> RANK = Map.of("SUGGESTED", 0, "FREE", 1, "BUSY", 2, "TRAVEL", 3, "LEAVE", 4);

    private final ScheduleDataLoader loader;
    private final PeriodTemplateService templates;
    private final TravelTimeService travel;
    private final TeachingScheduleAccessQuery access;
    private final UserDirectoryPort users;
    private final ObjectProvider<Clock> clock;

    public ScheduleSlotOptionsService(ScheduleDataLoader loader, PeriodTemplateService templates, TravelTimeService travel,
            TeachingScheduleAccessQuery access, UserDirectoryPort users, ObjectProvider<Clock> clock) {
        this.loader = loader;
        this.templates = templates;
        this.travel = travel;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    /** The cell a request asks about; {@code overlaps} compares an occurrence with its time window. */
    private record Cell(String tenantId, String schoolId, LocalDate date, int periodNo, OffsetDateTime start, OffsetDateTime end,
            Occurrence self) {
        boolean overlaps(Occurrence o) {
            return o.start().isBefore(end) && start.isBefore(o.end());
        }
    }

    // not read-only: the first template look-up of a tenant seeds its default period template
    @Transactional("postgresTransactionManager")
    public SlotOptionsResponse options(String tenantId, String schoolId, String batchId, LocalDate date, int periodNo,
            String classId, String excludeEventId) {
        String schoolName = loader.names(tenantId, "education_units", "name", Set.of(schoolId)).get(schoolId);
        if (schoolName == null) throw TeachingScheduleException.notFound("Trường không tồn tại: " + schoolId);
        PeriodSlot slot = templates.slot(templates.templateForSchool(tenantId, schoolId), periodNo);
        OffsetDateTime start = date.atTime(slot.start()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
        OffsetDateTime end = date.atTime(slot.end()).atZone(ScheduleDataLoader.VN).toOffsetDateTime();
        // the asked cell as an occurrence, so travel lines and leave checks can reuse the engine helpers
        Occurrence self = new Occurrence(null, "cell", null, null, null, "DRAFT", schoolId, schoolName, null, null, null, null, null,
                List.of(), date, date.getDayOfWeek().getValue(), periodNo, start, end, null, null, null, true);
        Cell cell = new Cell(tenantId, schoolId, date, periodNo, start, end, self);
        LocalDate monday = ScheduleConflictEngine.weekStart(date);
        List<Occurrence> loaded = loader.loadWeek(tenantId, monday.minusWeeks(1).atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime(),
                monday.plusWeeks(1).atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime(), null, false, batchId);
        List<Occurrence> week = loaded.stream().filter(o -> !o.date().isBefore(monday) && !Objects.equals(o.eventId(), excludeEventId)).toList();
        // same weekday and period of the week before, at this school
        List<Occurrence> lastWeek = loaded.stream().filter(o -> o.date().equals(date.minusWeeks(1)) && o.periodNo() == periodNo
                && o.schoolId().equals(schoolId)).toList();

        List<ClassRef> allClasses = loader.loadClasses(tenantId, schoolId);
        Integer grade = classId == null ? null : allClasses.stream().filter(c -> c.id().equals(classId)).map(ClassRef::grade)
                .filter(Objects::nonNull).findFirst().orElse(null);
        return new SlotOptionsResponse(schoolName, grade, classOptions(cell, allClasses, grade, week, lastWeek),
                teacherOptions(cell, classId, week, lastWeek), roomOptions(cell, classId, week));
    }

    private List<SlotClassOption> classOptions(Cell cell, List<ClassRef> classes, Integer grade, List<Occurrence> week,
            List<Occurrence> lastWeek) {
        Set<String> lastWeekClasses = lastWeek.stream().map(Occurrence::classId).collect(Collectors.toSet());
        return classes.stream().filter(c -> grade == null || grade.equals(c.grade())).map(c -> {
            Occurrence busy = week.stream().filter(o -> c.id().equals(o.classId()) && cell.overlaps(o)).findFirst().orElse(null);
            String state = busy != null ? "BUSY" : lastWeekClasses.contains(c.id()) ? "SUGGESTED" : "FREE";
            return new SlotClassOption(c.id(), c.name(), c.grade(), state, busy == null ? null : ScheduleConflictEngine.side(busy, null));
        }).sorted(Comparator.comparing((SlotClassOption o) -> RANK.get(o.state())).thenComparing(SlotClassOption::className)).toList();
    }

    private List<SlotTeacherOption> teacherOptions(Cell cell, String classId, List<Occurrence> week, List<Occurrence> lastWeek) {
        OffsetDateTime now = OffsetDateTime.now(clock.getIfAvailable(Clock::systemUTC));
        List<UserAccountProjection> pool = users.findCalendarTeachersByTenant(cell.tenantId());
        Set<String> ids = pool.stream().map(UserAccountProjection::id).collect(Collectors.toSet());
        Map<String, List<Leave>> leaves = loader.loadLeaves(cell.tenantId(), ids, cell.date(), cell.date());
        Set<String> accessed = new HashSet<>();
        access.held(cell.tenantId(), ids, now).stream().filter(h -> classId == null ? cell.schoolId().equals(h.schoolId()) : classId.equals(h.classId()))
                .forEach(h -> accessed.add(h.teacherId()));
        Map<String, List<Occurrence>> byTeacher = new HashMap<>();
        week.forEach(o -> o.teachers().forEach(t -> byTeacher.computeIfAbsent(t.id(), k -> new ArrayList<>()).add(o)));

        List<SlotTeacherOption> result = new ArrayList<>();
        for (UserAccountProjection teacher : pool) {
            List<Occurrence> mine = byTeacher.getOrDefault(teacher.id(), List.of());
            Occurrence busy = mine.stream().filter(cell::overlaps).findFirst().orElse(null);
            Leave leave = leaves.getOrDefault(teacher.id(), List.of()).stream().filter(l -> l.covers(cell.self())).findFirst().orElse(null);
            Occurrence before = mine.stream().filter(o -> o.date().equals(cell.date()) && !o.end().isAfter(cell.start()))
                    .max(Comparator.comparing(Occurrence::end)).orElse(null);
            Occurrence after = mine.stream().filter(o -> o.date().equals(cell.date()) && !o.start().isBefore(cell.end()))
                    .min(Comparator.comparing(Occurrence::start)).orElse(null);
            TravelGapResponse gap = travelGap(cell, before, after);
            boolean taughtHere = lastWeek.stream().anyMatch(o -> o.hasTeacher(teacher.id()));
            String state = busy != null ? "BUSY" : gap != null ? "TRAVEL" : leave != null ? "LEAVE" : taughtHere ? "SUGGESTED" : "FREE";
            Occurrence neighbor = nearestSameSchool(cell, before, after);
            result.add(new SlotTeacherOption(teacher.id(), TeacherShortName.of(teacher.displayName()),
                    TeacherShortName.initials(teacher.displayName()), state, mine.size(),
                    neighbor == null ? null : ScheduleConflictEngine.side(neighbor, teacher.id()), taughtHere,
                    accessed.contains(teacher.id()) ? "EXISTING" : "NEW",
                    busy == null ? null : ScheduleConflictEngine.side(busy, teacher.id()), gap,
                    leave == null ? null : new LeaveInfoResponse(leave.from().toString(), leave.to().toString(), leave.part())));
        }
        result.sort(Comparator.comparing((SlotTeacherOption o) -> RANK.get(o.state()))
                .thenComparing((SlotTeacherOption o) -> !o.taughtHereLastWeek()).thenComparingInt(SlotTeacherOption::weeklyPeriods)
                .thenComparing(SlotTeacherOption::teacherName));
        return result;
    }

    /** A neighbour at another school closer than the school pair needs; the earlier period of the two is the "from". */
    private TravelGapResponse travelGap(Cell cell, Occurrence before, Occurrence after) {
        if (before != null && !before.schoolId().equals(cell.schoolId())) {
            int gap = (int) Duration.between(before.end(), cell.start()).toMinutes();
            int required = travel.requiredMinutes(cell.tenantId(), before.schoolId(), cell.schoolId());
            if (gap < required) return ScheduleConflictEngine.travelGap(before, cell.self(), gap, required);
        }
        if (after != null && !after.schoolId().equals(cell.schoolId())) {
            int gap = (int) Duration.between(cell.end(), after.start()).toMinutes();
            int required = travel.requiredMinutes(cell.tenantId(), cell.schoolId(), after.schoolId());
            if (gap < required) return ScheduleConflictEngine.travelGap(cell.self(), after, gap, required);
        }
        return null;
    }

    private static Occurrence nearestSameSchool(Cell cell, Occurrence before, Occurrence after) {
        boolean beforeSame = before != null && before.schoolId().equals(cell.schoolId());
        boolean afterSame = after != null && after.schoolId().equals(cell.schoolId());
        if (beforeSame && afterSame) {
            return Duration.between(before.end(), cell.start()).compareTo(Duration.between(cell.end(), after.start())) <= 0 ? before : after;
        }
        return beforeSame ? before : afterSame ? after : null;
    }

    private List<SlotRoomOption> roomOptions(Cell cell, String classId, List<Occurrence> week) {
        List<RoomRef> rooms = loader.loadRooms(cell.tenantId(), cell.schoolId());
        String usual = usualRoom(cell, classId);
        List<SlotRoomOption> result = new ArrayList<>();
        for (RoomRef room : rooms) {
            Occurrence busy = week.stream().filter(o -> room.id().equals(o.roomId()) && cell.overlaps(o)).findFirst().orElse(null);
            boolean isUsual = room.id().equals(usual);
            result.add(new SlotRoomOption(room.id(), room.name(), room.seatCount(), busy != null ? "BUSY" : isUsual ? "SUGGESTED" : "FREE",
                    isUsual, busy == null ? null : ScheduleConflictEngine.side(busy, null)));
        }
        result.sort(Comparator.comparing((SlotRoomOption o) -> RANK.get(o.state())).thenComparing(SlotRoomOption::roomName));
        return result;
    }

    /** The room that holds at least half of the class's published periods of the last four weeks, if any. */
    private String usualRoom(Cell cell, String classId) {
        if (classId == null) return null;
        OffsetDateTime to = cell.date().atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime();
        Map<String, Integer> usage = loader.roomUsage(cell.tenantId(), classId, to.minusWeeks(4), to);
        int total = usage.values().stream().mapToInt(Integer::intValue).sum();
        return usage.entrySet().stream().filter(e -> e.getKey() != null && total > 0 && e.getValue() * 2 >= total)
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }
}
