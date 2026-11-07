package com.erg.ergspringboot.timekeeping;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Request/response shapes of the "my timekeeping" API (`/api/v1/timekeeping/me`). Times are server times. */
public final class TimekeepingViews {

    private TimekeepingViews() {}

    public record ClockView(Instant serverTime) {}

    /** What the device reported; there is deliberately no time field, the server clock decides. */
    public record PunchCommand(
            PunchDirection direction, Double lat, Double lng, Double accuracyM, boolean mockReported, String reason,
            byte[] photo) {}

    public record SiteView(String id, String name, double lat, double lng, int radiusM) {}

    public record ShiftView(String id, String name, LocalTime start, LocalTime end, BigDecimal workUnits) {}

    public record PeriodView(Instant start, Instant end, String className, String subject, String room) {}

    /** {@code method}: GPS, GPS_FACE, REMOTE or REMOTE_FACE. */
    public record PunchView(
            String id, String code, PunchDirection direction, Instant serverTime, PunchStatus status, String siteName,
            Integer distanceM, String method, boolean remote, String reason) {}

    public record TodayView(
            LocalDate date,
            Instant serverTime,
            ShiftView shift,
            List<SiteView> sites,
            boolean requireFace,
            boolean allowOutside,
            boolean faceConsented,
            boolean remote,
            int maxAccuracyM,
            PunchView in,
            PunchView out,
            List<PeriodView> periods,
            LocalTime expectedOut,
            boolean periodLocked) {}

    /**
     * {@code alreadyPunched}: an IN for this shift already existed and is returned unchanged.
     * {@code minutesVsShift}: positive = late (IN) / after the shift end (OUT), negative = early.
     */
    public record PunchResult(
            PunchStatus verdict, PunchView punch, boolean alreadyPunched, Integer minutesVsShift,
            PeriodView nextPeriod, String messageVi) {}

    /** {@code flags}: REMOTE, FLAGGED, PENDING_REVIEW. */
    public record MonthDayView(LocalDate date, DayStatus status, BigDecimal units, List<String> flags) {}

    public record MonthView(
            String period,
            BigDecimal actualUnits,
            BigDecimal standardUnits,
            int lateCount,
            int lateMinutes,
            BigDecimal annualLeaveRemaining,
            long pendingRequests,
            int periods,
            List<MonthDayView> days,
            LocalDate reviewUntil,
            boolean locked) {}

    /** {@code photoUrl} is a 5-minute signed URL; {@code photoPurged} once the 60-day retention removed it. */
    public record DayPunchView(PunchView punch, String photoUrl, boolean photoPurged) {}

    public record DayDetailView(
            LocalDate date,
            DayStatus status,
            ShiftView shift,
            DayPunchView in,
            DayPunchView out,
            List<DayPunchView> punches,
            Missing missing,
            String missingReason,
            List<PeriodView> periods,
            BigDecimal units,
            int lateMinutes,
            int earlyMinutes,
            boolean remote,
            LocalTime suggestedOut,
            int explanationsRemaining,
            List<String> flags) {}

    /** Bytes of a locally stored punch photo, for the dev-only signed file endpoint. */
    public record PhotoFile(byte[] bytes, String contentType) {}
}
