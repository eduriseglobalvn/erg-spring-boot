package com.erg.ergspringboot.timekeeping.internal.punch;

import com.erg.ergspringboot.common.request.notifications.NotificationRequests.AdminSendRequest;
import com.erg.ergspringboot.notifications.NotificationAdminOperations;
import com.erg.ergspringboot.platform.events.ClusterJobLock;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkShiftRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ten minutes before a shift ends, reminds everyone with an IN and no OUT to punch out. Runs every few minutes,
 * so one shift is seen by two runs: the outbox dedupe key {@code tk-out:{userId}:{workDate}} keeps it to one
 * notification per person and day (no local "already sent" state).
 */
@Component
public class PunchOutReminderJob {

    static final String JOB_NAME = "timekeeping-punch-out-reminder";
    private static final int LEAD_MINUTES = 10;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Logger log = LoggerFactory.getLogger(PunchOutReminderJob.class);

    private final TkShiftRepository shifts;
    private final TkPunchRepository punches;
    private final NotificationAdminOperations notifications;
    private final ClusterJobLock jobLock;
    private final Clock clock;

    PunchOutReminderJob(TkShiftRepository shifts, TkPunchRepository punches, NotificationAdminOperations notifications,
            ClusterJobLock jobLock, Clock clock) {
        this.shifts = shifts;
        this.punches = punches;
        this.notifications = notifications;
        this.jobLock = jobLock;
        this.clock = clock;
    }

    @Scheduled(cron = "${erg.timekeeping.reminder-cron:0 */5 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void run() {
        jobLock.callExclusively(JOB_NAME, this::remindDue);
    }

    /** Returns how many reminders were handed to the outbox (repeats are absorbed there). */
    public int remindDue() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), WorkContextResolver.ZONE)
                .truncatedTo(ChronoUnit.MINUTES);
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        LocalTime upTo = nowTime.plusMinutes(LEAD_MINUTES);
        if (upTo.isBefore(nowTime)) return 0; // window would cross midnight; shifts end within the day
        List<TkShift> due;
        try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.maintenance())) {
            due = shifts.findEndingBetween(today.getDayOfWeek().getValue(), nowTime, upTo);
        }
        int sent = 0;
        for (TkShift shift : due) {
            try (TenantContext.Scope ignored = TenantContext.scope(TenantContext.of(shift.getTenantId(), null, "system"))) {
                for (String userId : punches.findUsersInWithoutOut(shift.getTenantId(), shift.getId(), today)) {
                    if (send(shift, userId, today)) sent++;
                }
            }
        }
        return sent;
    }

    private boolean send(TkShift shift, String userId, LocalDate today) {
        try {
            notifications.send(shift.getTenantId(), "system", new AdminSendRequest(
                    "tk-out:" + userId + ":" + today,
                    "TIMEKEEPING_PUNCH_OUT_REMINDER",
                    "timekeeping_shift",
                    shift.getId(),
                    "Nhắc chấm ra",
                    shift.getName() + " kết thúc lúc " + shift.getEndTime().format(HH_MM) + ". Nhớ chấm ra.",
                    "/teacher/timekeeping/punch",
                    "normal",
                    "lms",
                    List.of(userId),
                    null,
                    Map.of()));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Punch-out reminder failed for user {}: {}", userId, ex.getClass().getSimpleName());
            return false;
        }
    }
}
