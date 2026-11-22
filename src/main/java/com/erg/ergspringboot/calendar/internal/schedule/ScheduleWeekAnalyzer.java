package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.CalendarCaches;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One week of the tenant as the overview screens see it: every PUBLISHED and DRAFT event of the given schools and the
 * conflicts between them and against the other schools' published events. Two drafts of different batches are not in
 * conflict by rule (KE_HOACH_LICH_DAY.md 2.2), so those pairs are dropped here.
 */
@Service
public class ScheduleWeekAnalyzer {

    /** {@code details} has every hit from both sides of a pair (cells need that); {@code pairs} counts a pair once. */
    public record Week(LocalDate monday, List<Occurrence> events, List<ConflictDetailResponse> details,
            List<ConflictDetailResponse> pairs) {}

    private final ScheduleDataLoader loader;
    private final ScheduleConflictEngine engine;

    public ScheduleWeekAnalyzer(ScheduleDataLoader loader, ScheduleConflictEngine engine) {
        this.loader = loader;
        this.engine = engine;
    }

    /**
     * Cached 20 s per (tenant, ISO week, school scope; null = every school): an overview click re-reads the same week, and
     * every calendar write evicts it through {@code @CalendarCacheEvict}. The key's week number is {@code (epochDay + 3) / 7},
     * the same for Monday to Sunday.
     */
    @Cacheable(cacheNames = CalendarCaches.OVERVIEW_WEEK, key = "{#tenantId, (#anyDayOfWeek.toEpochDay() + 3) / 7, "
            + "(#schoolIds == null ? '*' : new java.util.TreeSet(#schoolIds))}")
    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public Week analyze(String tenantId, LocalDate anyDayOfWeek, Collection<String> schoolIds) {
        LocalDate monday = ScheduleConflictEngine.weekStart(anyDayOfWeek);
        OffsetDateTime from = monday.atStartOfDay(ScheduleDataLoader.VN).toOffsetDateTime();
        List<Occurrence> events = loader.loadWeek(tenantId, from, from.plusWeeks(1), schoolIds, true, null);
        List<ConflictDetailResponse> details = engine.detectCovering(tenantId, events, schoolIds).stream()
                .filter(c -> !draftsOfDifferentBatches(c)).toList();
        Set<String> seen = new HashSet<>();
        List<ConflictDetailResponse> pairs = details.stream().filter(c -> seen.add(pairKey(c))).toList();
        return new Week(monday, events, details, pairs);
    }

    private static boolean draftsOfDifferentBatches(ConflictDetailResponse c) {
        return c.other() != null && "DRAFT".equals(c.mine().batchStatus()) && "DRAFT".equals(c.other().batchStatus())
                && !Objects.equals(c.mine().batchId(), c.other().batchId());
    }

    /** Both sides of one clash give the same key: kind, the two events (order-free) and the teacher it is about. */
    private static String pairKey(ConflictDetailResponse c) {
        String a = ScheduleConflictEngine.nz(c.mine().eventId());
        boolean teacherKind = c.kind().startsWith("TEACHER") || c.kind().startsWith("TRAVEL");
        String b = c.other() == null ? "" : ScheduleConflictEngine.nz(c.other().eventId());
        return c.kind() + "|" + (a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a) + "|" + (teacherKind ? ScheduleConflictEngine.nz(c.mine().teacherId()) : "");
    }
}
