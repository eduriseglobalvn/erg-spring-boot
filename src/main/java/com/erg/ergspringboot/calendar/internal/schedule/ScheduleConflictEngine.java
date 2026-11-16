package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.SchoolShortName;
import com.erg.ergspringboot.calendar.TeacherShortName;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Leave;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.RoomRef;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.FixSuggestionResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LeaveInfoResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SideResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TravelGapResponse;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teaching schedule conflict engine v2 (KE_HOACH_LICH_DAY.md 2.2). Compares the occurrences being scheduled against
 * PUBLISHED events of every batch plus each other (the own batch's drafts are the candidates themselves). Drafts of
 * other batches and cancelled events never count.
 */
@Service
public class ScheduleConflictEngine {

    static final String TEACHER_TIME = "TEACHER_TIME";
    static final String CLASS_TIME = "CLASS_TIME";
    static final String ROOM_TIME = "ROOM_TIME";
    static final String TRAVEL_TIME = "TRAVEL_TIME";
    static final String TEACHER_ON_LEAVE = "TEACHER_ON_LEAVE";

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final ScheduleDataLoader loader;
    private final TravelTimeService travel;
    private final UserDirectoryPort users;

    public ScheduleConflictEngine(ScheduleDataLoader loader, TravelTimeService travel, UserDirectoryPort users) {
        this.loader = loader;
        this.travel = travel;
        this.users = users;
    }

    /** One occurrence of the candidate colliding with another occurrence (or with a leave when {@code other} is null). */
    private record Hit(String kind, Occurrence mine, Occurrence other, TeacherRef focus, Integer gap, Integer required,
            Occurrence earlier, Leave leave) {}

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    /** {@code withSuggestions} false skips the per-conflict fix search (it queries the DB): overview and gates do not show it. */
    List<ConflictDetailResponse> detect(String tenantId, List<Occurrence> candidates, Set<String> excludeEventIds,
            boolean withSuggestions) {
        if (candidates.isEmpty()) return List.of();
        Set<String> classIds = candidates.stream().map(Occurrence::classId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> roomIds = candidates.stream().map(Occurrence::roomId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> teacherIds = candidates.stream().flatMap(c -> c.teachers().stream()).map(TeacherRef::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate firstDate = candidates.stream().map(Occurrence::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate lastDate = candidates.stream().map(Occurrence::date).max(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime from = firstDate.atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime();
        OffsetDateTime to = lastDate.plusDays(1).atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime();

        List<Occurrence> existing = loader.loadPublished(tenantId, from, to, classIds, roomIds, teacherIds, excludeEventIds);
        Map<String, List<Leave>> leaves = loader.loadLeaves(tenantId, teacherIds, firstDate, lastDate);
        Context ctx = new Context(tenantId, candidates, existing, excludeEventIds, from, to, withSuggestions);

        List<Hit> hits = new ArrayList<>();
        for (Occurrence c : candidates) {
            for (Occurrence x : ctx.byClass.getOrDefault(c.classId(), List.of())) {
                if (ctx.differentAndOverlapping(c, x)) hits.add(new Hit(CLASS_TIME, c, x, null, null, null, null, null));
            }
            if (c.roomId() != null) {
                for (Occurrence x : ctx.byRoom.getOrDefault(c.roomId(), List.of())) {
                    if (ctx.differentAndOverlapping(c, x)) hits.add(new Hit(ROOM_TIME, c, x, null, null, null, null, null));
                }
            }
            for (TeacherRef t : c.teachers()) {
                List<Occurrence> theirs = ctx.byTeacher.getOrDefault(t.id(), List.of());
                for (Occurrence x : theirs) {
                    if (ctx.differentAndOverlapping(c, x)) hits.add(new Hit(TEACHER_TIME, c, x, t, null, null, null, null));
                }
                travelHit(ctx, c, t, theirs).ifPresent(hits::add);
                for (Leave leave : leaves.getOrDefault(t.id(), List.of())) {
                    if (leave.covers(c)) {
                        hits.add(new Hit(TEACHER_ON_LEAVE, c, null, t, null, null, null, leave));
                        break;
                    }
                }
            }
        }
        return group(ctx, hits);
    }

    /** Nearest event before (or, for already-published events, after) the candidate that is too close to reach. */
    private Optional<Hit> travelHit(Context ctx, Occurrence c, TeacherRef t, List<Occurrence> theirs) {
        Occurrence before = null;
        Occurrence after = null;
        for (Occurrence x : theirs) {
            if (x == c || x.sameEvent(c) || !x.date().equals(c.date())) continue;
            if (!x.end().isAfter(c.start()) && (before == null || x.end().isAfter(before.end()))) before = x;
            if (!x.mine() && !x.start().isBefore(c.end()) && (after == null || x.start().isBefore(after.start()))) after = x;
        }
        if (before != null && !before.schoolId().equals(c.schoolId())) {
            int gap = (int) Duration.between(before.end(), c.start()).toMinutes();
            int required = ctx.requiredMinutes(before.schoolId(), c.schoolId());
            if (gap < required) return Optional.of(new Hit(TRAVEL_TIME, c, before, t, gap, required, before, null));
        }
        if (after != null && !after.schoolId().equals(c.schoolId())) {
            int gap = (int) Duration.between(c.end(), after.start()).toMinutes();
            int required = ctx.requiredMinutes(c.schoolId(), after.schoolId());
            if (gap < required) return Optional.of(new Hit(TRAVEL_TIME, c, after, t, gap, required, c, null));
        }
        return Optional.empty();
    }

    private List<ConflictDetailResponse> group(Context ctx, List<Hit> hits) {
        Map<String, List<Hit>> groups = new LinkedHashMap<>();
        for (Hit hit : hits) {
            groups.computeIfAbsent(hit.kind() + "|" + hit.mine().rowKey() + "|" + signature(hit.other()) + "|"
                    + (hit.focus() == null ? "-" : hit.focus().id()), k -> new ArrayList<>()).add(hit);
        }
        Map<String, Long> weeksPerRow = new HashMap<>();
        ctx.candidates.stream().collect(Collectors.groupingBy(Occurrence::rowKey)).forEach((row, list) ->
                weeksPerRow.put(row, list.stream().map(o -> weekStart(o.date())).distinct().count()));
        List<ConflictDetailResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<Hit>> entry : groups.entrySet()) {
            List<Hit> group = entry.getValue().stream().sorted(Comparator.comparing(h -> h.mine().start())).toList();
            result.add(detail(ctx, entry.getKey(), group, weeksPerRow.getOrDefault(group.get(0).mine().rowKey(), 1L).intValue()));
        }
        return result;
    }

    private ConflictDetailResponse detail(Context ctx, String key, List<Hit> group, int totalWeeks) {
        Hit first = group.get(0);
        Occurrence c = first.mine();
        String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        int affectedWeeks = (int) group.stream().map(h -> weekStart(h.mine().date())).distinct().count();
        boolean leave = TEACHER_ON_LEAVE.equals(first.kind());
        TravelGapResponse gap = null;
        if (TRAVEL_TIME.equals(first.kind())) {
            Occurrence earlier = first.earlier();
            Occurrence later = earlier == first.other() ? c : first.other();
            gap = travelGap(earlier, later, first.gap(), first.required());
        }
        LeaveInfoResponse leaveInfo = leave ? new LeaveInfoResponse(first.leave().from().toString(),
                first.leave().to().toString(), first.leave().part()) : null;
        String teacherId = first.focus() == null ? null : first.focus().id();
        return new ConflictDetailResponse(id, first.kind(), leave ? "WARNING" : "ERROR", c.clientRowId(), c.weekday(),
                c.periodNo(), c.start().format(HH_MM), c.end().format(HH_MM), c.date().toString(), affectedWeeks,
                totalWeeks, side(c, teacherId), first.other() == null ? null : side(first.other(), teacherId), gap,
                leaveInfo, leave || !ctx.withSuggestions ? null : suggestFix(ctx, group), message(first));
    }

    // ---- fix suggestions ---------------------------------------------------------------------------------------

    private FixSuggestionResponse suggestFix(Context ctx, List<Hit> group) {
        Hit first = group.get(0);
        return switch (first.kind()) {
            case TEACHER_TIME -> suggestTeacher(ctx, first.mine().schoolId(), group.stream().map(Hit::mine).toList(),
                    first.mine(), "CHANGE_TEACHER", "FREE");
            case TRAVEL_TIME -> {
                Occurrence previous = first.earlier();
                List<Occurrence> windows = group.stream().map(Hit::earlier).toList();
                yield suggestTeacher(ctx, previous.schoolId(), windows, previous, "CHANGE_PREVIOUS_TEACHER", "SAME_SCHOOL");
            }
            case ROOM_TIME -> suggestRoom(ctx, group);
            default -> null;
        };
    }

    /** First free teacher of the school for every window: taught here before, then lightest week, then name. */
    private FixSuggestionResponse suggestTeacher(Context ctx, String schoolId, List<Occurrence> windows, Occurrence target,
            String kind, String reason) {
        Set<String> exclude = target.teachers().stream().map(TeacherRef::id).collect(Collectors.toSet());
        List<UserAccountProjection> pool = users.findCalendarTeachersByTenantAndSchoolIds(ctx.tenantId, List.of(schoolId)).stream()
                .filter(u -> !exclude.contains(u.id())).toList();
        if (pool.isEmpty()) return null;
        Set<String> poolIds = pool.stream().map(UserAccountProjection::id).collect(Collectors.toSet());
        List<Occurrence> busy = new ArrayList<>(loader.loadPublished(ctx.tenantId, ctx.from, ctx.to, Set.of(), Set.of(), poolIds,
                ctx.excludeEventIds));
        busy.addAll(ctx.candidates);
        Set<String> taughtHere = loader.teachersWhoTaughtAt(ctx.tenantId, schoolId, poolIds);
        LocalDate week = weekStart(windows.get(0).date());
        UserAccountProjection best = pool.stream()
                .filter(u -> windows.stream().noneMatch(w -> busy.stream().anyMatch(b -> b.hasTeacher(u.id()) && b.overlaps(w))))
                .min(Comparator.<UserAccountProjection, Boolean>comparing(u -> !taughtHere.contains(u.id()))
                        .thenComparingLong(u -> busy.stream().filter(b -> b.hasTeacher(u.id()) && weekStart(b.date()).equals(week)).count())
                        .thenComparing(u -> nz(u.displayName())))
                .orElse(null);
        if (best == null) return null;
        return new FixSuggestionResponse(kind, target.eventId(), target.clientRowId(), target.className(), best.id(),
                TeacherShortName.of(best.displayName()), null, null, reason);
    }

    private FixSuggestionResponse suggestRoom(Context ctx, List<Hit> group) {
        Occurrence c = group.get(0).mine();
        List<RoomRef> rooms = loader.loadRooms(ctx.tenantId, c.schoolId());
        Integer currentSeats = rooms.stream().filter(r -> r.id().equals(c.roomId())).map(RoomRef::seatCount)
                .filter(Objects::nonNull).findFirst().orElse(null);
        Set<String> roomIds = rooms.stream().map(RoomRef::id).collect(Collectors.toSet());
        List<Occurrence> busy = new ArrayList<>(loader.loadPublished(ctx.tenantId, ctx.from, ctx.to, Set.of(), roomIds, Set.of(),
                ctx.excludeEventIds));
        busy.addAll(ctx.candidates);
        List<Occurrence> windows = group.stream().map(Hit::mine).toList();
        RoomRef best = rooms.stream().filter(r -> !r.id().equals(c.roomId()))
                .filter(r -> windows.stream().noneMatch(w -> busy.stream().anyMatch(b -> r.id().equals(b.roomId()) && b.overlaps(w))))
                .min(Comparator.<RoomRef, Integer>comparing(r -> r.seatCount() == null || currentSeats == null
                                ? Integer.MAX_VALUE : Math.abs(r.seatCount() - currentSeats))
                        .thenComparing(RoomRef::name))
                .orElse(null);
        if (best == null) return null;
        return new FixSuggestionResponse("CHANGE_ROOM", c.eventId(), c.clientRowId(), c.className(), null, null, best.id(),
                best.name(), "FREE");
    }

    static TravelGapResponse travelGap(Occurrence earlier, Occurrence later, int gapMinutes, int requiredMinutes) {
        return new TravelGapResponse(earlier.schoolId(), SchoolShortName.initials(nz(earlier.schoolName())), nz(earlier.schoolName()),
                later.schoolId(), SchoolShortName.initials(nz(later.schoolName())), nz(later.schoolName()),
                earlier.end().format(HH_MM), gapMinutes, requiredMinutes);
    }

    // ---- presentation ------------------------------------------------------------------------------------------

    static SideResponse side(Occurrence o, String focusTeacherId) {
        TeacherRef teacher = o.teachers().stream().filter(t -> t.id().equals(focusTeacherId)).findFirst()
                .or(() -> o.teachers().stream().filter(t -> "MAIN".equals(t.role())).findFirst())
                .or(() -> o.teachers().stream().findFirst()).orElse(null);
        return new SideResponse(o.eventId(), teacher == null ? null : teacher.id(),
                teacher == null ? null : TeacherShortName.of(teacher.name()), o.classId(), o.className(), o.roomId(),
                o.roomName(), o.schoolId(), nz(o.schoolName()), SchoolShortName.of(o.schoolName()), o.subjectName(),
                o.batchId(), o.batchLabel(), o.batchStatus(), o.createdByName(), o.createdByRole(),
                o.createdAt() == null ? null : o.createdAt().toString(), o.periodNo(), o.start().format(HH_MM),
                o.end().format(HH_MM));
    }

    private static String message(Hit hit) {
        String teacher = hit.focus() == null ? "" : TeacherShortName.of(hit.focus().name());
        Occurrence c = hit.mine();
        Occurrence x = hit.other();
        return switch (hit.kind()) {
            case TEACHER_TIME -> teacher + " đang dạy " + x.className() + " ở " + nz(x.schoolName()) + " cùng giờ";
            case CLASS_TIME -> "Lớp " + c.className() + " đã có tiết khác cùng giờ (" + nz(x.subjectName()) + ")";
            case ROOM_TIME -> "Phòng " + nz(c.roomName()) + " đã có lớp " + x.className() + " cùng giờ";
            case TRAVEL_TIME -> teacher + " chỉ có " + hit.gap() + " phút để di chuyển từ "
                    + nz(hit.earlier().schoolName()) + " sang "
                    + nz((hit.earlier() == x ? c : x).schoolName()) + " (cần ít nhất " + hit.required() + " phút)";
            default -> teacher + " nghỉ " + switch (hit.leave().part()) {
                case "AM" -> "buổi sáng";
                case "PM" -> "buổi chiều";
                default -> "cả ngày";
            } + " từ " + hit.leave().from().format(DD_MM) + " đến " + hit.leave().to().format(DD_MM);
        };
    }

    private static String signature(Occurrence x) {
        return x == null ? "-" : String.join("|", nz(x.schoolId()), nz(x.classId()), nz(x.roomId()),
                String.valueOf(x.periodNo()), String.valueOf(x.weekday()), nz(x.batchId()), nz(x.rowKey()).isEmpty() || x.mine()
                        ? nz(x.rowKey()) : "");
    }

    static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Per-call lookups over candidates plus the published events they could collide with. */
    private final class Context {
        final String tenantId;
        final List<Occurrence> candidates;
        final Set<String> excludeEventIds;
        final OffsetDateTime from;
        final OffsetDateTime to;
        final boolean withSuggestions;
        final Map<String, List<Occurrence>> byClass = new HashMap<>();
        final Map<String, List<Occurrence>> byRoom = new HashMap<>();
        final Map<String, List<Occurrence>> byTeacher = new HashMap<>();
        private final Map<String, Integer> requiredByPair = new HashMap<>();

        Context(String tenantId, List<Occurrence> candidates, List<Occurrence> existing, Set<String> excludeEventIds,
                OffsetDateTime from, OffsetDateTime to, boolean withSuggestions) {
            this.tenantId = tenantId;
            this.withSuggestions = withSuggestions;
            this.candidates = candidates;
            this.excludeEventIds = excludeEventIds;
            this.from = from;
            this.to = to;
            Collection<Occurrence> all = new ArrayList<>(existing);
            all.addAll(candidates);
            for (Occurrence o : all) {
                byClass.computeIfAbsent(o.classId(), k -> new ArrayList<>()).add(o);
                if (o.roomId() != null) byRoom.computeIfAbsent(o.roomId(), k -> new ArrayList<>()).add(o);
                for (TeacherRef t : o.teachers()) byTeacher.computeIfAbsent(t.id(), k -> new ArrayList<>()).add(o);
            }
        }

        boolean differentAndOverlapping(Occurrence c, Occurrence x) {
            return x != c && !x.sameEvent(c) && c.overlaps(x);
        }

        int requiredMinutes(String schoolA, String schoolB) {
            String key = schoolA.compareTo(schoolB) <= 0 ? schoolA + "|" + schoolB : schoolB + "|" + schoolA;
            return requiredByPair.computeIfAbsent(key, k -> travel.requiredMinutes(tenantId, schoolA, schoolB));
        }
    }
}
