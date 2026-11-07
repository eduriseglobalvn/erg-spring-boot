package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.calendar.TeacherPeriod;
import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Teaching periods a leave would leave uncovered (D15: listed only, no substitute assignment). A half-day leave
 * splits at 12:00 Asia/Ho_Chi_Minh: AM = periods starting before noon, PM = the rest.
 */
@Service
public class AffectedPeriodsService {

    private static final LocalTime NOON = LocalTime.NOON;

    private final CalendarReadPort calendar;

    AffectedPeriodsService(CalendarReadPort calendar) {
        this.calendar = calendar;
    }

    public List<TeacherPeriod> affected(String tenantId, String userId, LocalDate from, LocalDate to, DayPart part) {
        return calendar.teacherPeriods(tenantId, userId, from, to).stream()
                .filter(p -> part == DayPart.FULL || (part == DayPart.AM) == morning(p))
                .toList();
    }

    private static boolean morning(TeacherPeriod period) {
        return period.start().atZone(WorkContextResolver.ZONE).toLocalTime().isBefore(NOON);
    }
}
