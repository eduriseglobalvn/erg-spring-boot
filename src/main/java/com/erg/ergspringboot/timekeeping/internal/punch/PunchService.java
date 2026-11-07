package com.erg.ergspringboot.timekeeping.internal.punch;

import com.erg.ergspringboot.calendar.CalendarReadPort;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PeriodView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchResult;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchView;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.evidence.FaceConsentService;
import com.erg.ergspringboot.timekeeping.internal.evidence.PunchPhotoService;
import com.erg.ergspringboot.timekeeping.internal.rules.GeoDistance;
import com.erg.ergspringboot.timekeeping.internal.rules.PunchCheck;
import com.erg.ergspringboot.timekeeping.internal.rules.PunchInput;
import com.erg.ergspringboot.timekeeping.internal.rules.PunchValidator;
import com.erg.ergspringboot.timekeeping.internal.rules.SitePolicy;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPunch;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import com.erg.ergspringboot.timekeeping.persistence.TkSite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records a punch at the server clock. Order: key, coordinates, replay of the same key, school/shift,
 * already-punched IN, period lock, punch window, face consent/photo, location verdict; only then the photo is
 * uploaded, the code drawn and the row saved. A REJECTED verdict is never stored.
 */
@Service
public class PunchService {

    static final int MAX_KEY_LENGTH = 100;
    private static final Duration IN_OPENS_BEFORE_START = Duration.ofMinutes(120);
    private static final Duration OUT_CLOSES_AFTER_END = Duration.ofMinutes(60);

    private final TkPunchRepository punches;
    private final WorkContextResolver context;
    private final PunchPhotoService photos;
    private final FaceConsentService consents;
    private final PunchCodeGenerator codes;
    private final DayAggregationService days;
    private final CalendarReadPort calendar;
    private final Clock clock;

    PunchService(TkPunchRepository punches, WorkContextResolver context, PunchPhotoService photos,
            FaceConsentService consents, PunchCodeGenerator codes, DayAggregationService days,
            CalendarReadPort calendar, Clock clock) {
        this.punches = punches;
        this.context = context;
        this.photos = photos;
        this.consents = consents;
        this.codes = codes;
        this.days = days;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional
    public PunchResult punch(String tenantId, String userId, String idempotencyKey, PunchCommand cmd) {
        String key = requireKey(idempotencyKey);
        requireValidReading(cmd);
        Optional<TkPunch> replay = punches.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, key);
        if (replay.isPresent()) return result(replay.get(), false, null);

        Instant now = clock.instant();
        LocalDate date = LocalDate.ofInstant(now, WorkContextResolver.ZONE);
        String schoolId = context.schoolOf(tenantId, userId).orElseThrow(() -> unprocessable("NO_SCHEDULE",
                "Bạn chưa được xếp ca chấm công"));
        TkShift shift = context.shiftFor(tenantId, schoolId, date).orElseThrow(() -> unprocessable("NO_SHIFT",
                "Hôm nay không có ca làm việc"));
        List<TkPunch> today = punches.findByTenantIdAndUserIdAndWorkDateOrderByServerTimeAscCodeAsc(tenantId, userId, date);
        if (cmd.direction() == PunchDirection.IN) {
            Optional<TkPunch> firstIn = DayAggregationService.firstIn(today);
            if (firstIn.isPresent()) return result(firstIn.get(), true, shift);
        }
        if (context.isLocked(tenantId, schoolId, date)) throw WorkContextResolver.periodLocked();
        requireInsideWindow(cmd.direction(), now, date, shift);

        TkPolicy policy = context.policyFor(tenantId, schoolId, date);
        boolean hasPhoto = cmd.photo() != null && cmd.photo().length > 0;
        if ((hasPhoto || policy.getRequireFace()) && !consents.hasConsent(tenantId, userId)) {
            throw new TimekeepingException(HttpStatus.CONFLICT, "FACE_CONSENT_REQUIRED",
                    "Cần đồng ý lưu ảnh khuôn mặt trước khi chấm công");
        }
        if (policy.getRequireFace() && !hasPhoto) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "FACE_REQUIRED", "Cần chụp ảnh khuôn mặt");
        }

        boolean remote = context.isRemote(tenantId, schoolId, userId, date, shift.getId());
        TkSite site = remote ? null : nearestSite(tenantId, schoolId, cmd);
        PunchCheck check = PunchValidator.check(
                new PunchInput(cmd.lat(), cmd.lng(), cmd.accuracyM(), cmd.mockReported(), remote, cmd.reason()),
                site == null ? new SitePolicy(0, 0, 0, false)
                        : new SitePolicy(site.getLat().doubleValue(), site.getLng().doubleValue(), site.getRadiusM(),
                                policy.getAllowOutside()));
        if (check.verdict() == PunchStatus.REJECTED) throw unprocessable("PUNCH_REJECTED", check.messageVi());

        TkPunch punch = new TkPunch();
        if (hasPhoto) punch.setPhotoKey(storePhotoDiscardedOnRollback(tenantId, userId, cmd.photo()));
        punch.setTenantId(tenantId);
        punch.setSchoolId(schoolId);
        punch.setUserId(userId);
        punch.setShiftId(shift.getId());
        punch.setWorkDate(date);
        punch.setDirection(cmd.direction());
        punch.setServerTime(now);
        punch.setStatus(check.verdict());
        punch.setRemote(remote);
        punch.setMockReported(cmd.mockReported());
        punch.setMethod((remote ? "REMOTE" : "GPS") + (hasPhoto ? "_FACE" : ""));
        if (!remote) {
            punch.setSiteId(site.getId());
            punch.setLat(BigDecimal.valueOf(cmd.lat()).setScale(6, RoundingMode.HALF_UP));
            punch.setLng(BigDecimal.valueOf(cmd.lng()).setScale(6, RoundingMode.HALF_UP));
            punch.setAccuracyM(cmd.accuracyM() == null ? null
                    : (int) Math.min(Math.round(cmd.accuracyM()), 1_000_000));
            punch.setDistanceM(check.distanceM());
        }
        punch.setReason(blankToNull(cmd.reason()));
        punch.setIdempotencyKey(key);
        punch.setCode(codes.next(tenantId, date));
        TkPunch saved = punches.saveAndFlush(punch);
        days.recompute(tenantId, userId, date);
        return result(saved, false, shift, site, check.messageVi());
    }

    /** Read-only replay for the facade after a concurrent insert of the same key lost the unique-constraint race. */
    @Transactional(readOnly = true)
    public Optional<PunchResult> replay(String tenantId, String userId, String idempotencyKey) {
        return punches.findByTenantIdAndUserIdAndIdempotencyKey(tenantId, userId, idempotencyKey)
                .map(p -> result(p, false, null));
    }

    private String storePhotoDiscardedOnRollback(String tenantId, String userId, byte[] photo) {
        String key = photos.storePunchPhoto(tenantId, userId, photo);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) photos.discardPhoto(key);
                }
            });
        }
        return key;
    }

    /** Nearest active site; when none is within its radius the nearest one still gives the distance message. */
    private TkSite nearestSite(String tenantId, String schoolId, PunchCommand cmd) {
        List<TkSite> sites = context.activeSites(tenantId, schoolId);
        if (sites.isEmpty()) throw unprocessable("NO_SITE", "Chưa cấu hình địa điểm chấm công");
        if (cmd.lat() == null || cmd.lng() == null) return sites.get(0);
        return sites.stream().min(Comparator.comparingDouble(s -> GeoDistance.haversineMeters(
                s.getLat().doubleValue(), s.getLng().doubleValue(), cmd.lat(), cmd.lng()))).orElseThrow();
    }

    private PunchResult result(TkPunch punch, boolean alreadyPunched, TkShift shift) {
        return result(punch, alreadyPunched, shift, null, null);
    }

    private PunchResult result(TkPunch punch, boolean alreadyPunched, TkShift shift, TkSite site, String messageVi) {
        Integer minutesVsShift = null;
        PeriodView next = null;
        if (shift != null) {
            LocalDateTime local = LocalDateTime.ofInstant(punch.getServerTime(), WorkContextResolver.ZONE);
            LocalDateTime reference = punch.getWorkDate().atTime(
                    punch.getDirection() == PunchDirection.IN ? shift.getStartTime() : shift.getEndTime());
            minutesVsShift = (int) Duration.between(reference, local).toMinutes();
            next = calendar.teacherPeriods(punch.getTenantId(), punch.getUserId(), punch.getWorkDate(),
                            punch.getWorkDate()).stream()
                    .filter(p -> p.start().isAfter(punch.getServerTime()))
                    .findFirst()
                    .map(p -> new PeriodView(p.start(), p.end(), p.className(), p.subject(), p.room()))
                    .orElse(null);
        }
        return new PunchResult(punch.getStatus(), view(punch, site == null ? null : site.getName()), alreadyPunched,
                minutesVsShift, next, messageVi);
    }

    public static PunchView view(TkPunch p, String siteName) {
        return new PunchView(p.getId(), p.getCode(), p.getDirection(), p.getServerTime(), p.getStatus(), siteName,
                p.getDistanceM(), p.getMethod(), Boolean.TRUE.equals(p.getRemote()), p.getReason());
    }

    private static void requireInsideWindow(PunchDirection direction, Instant now, LocalDate date, TkShift shift) {
        LocalDateTime start = date.atTime(shift.getStartTime());
        LocalDateTime end = date.atTime(shift.getEndTime());
        LocalDateTime opens = direction == PunchDirection.IN ? start.minus(IN_OPENS_BEFORE_START) : start;
        LocalDateTime closes = direction == PunchDirection.IN ? end : end.plus(OUT_CLOSES_AFTER_END);
        LocalDateTime local = LocalDateTime.ofInstant(now, WorkContextResolver.ZONE);
        if (local.isBefore(opens) || local.isAfter(closes)) {
            throw unprocessable("OUTSIDE_PUNCH_WINDOW", "Ngoài giờ chấm công");
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Thiếu hoặc sai header Idempotency-Key");
        }
        return key;
    }

    private static void requireValidReading(PunchCommand cmd) {
        if (cmd == null || cmd.direction() == null) badReading("Thiếu hướng chấm (vào/ra)");
        if (cmd.lat() != null && !(Double.isFinite(cmd.lat()) && Math.abs(cmd.lat()) <= 90)) badReading("Vĩ độ không hợp lệ");
        if (cmd.lng() != null && !(Double.isFinite(cmd.lng()) && Math.abs(cmd.lng()) <= 180)) badReading("Kinh độ không hợp lệ");
        if (cmd.accuracyM() != null && !(Double.isFinite(cmd.accuracyM()) && cmd.accuracyM() >= 0)) {
            badReading("Độ chính xác GPS không hợp lệ");
        }
        if (cmd.reason() != null && cmd.reason().length() > 500) badReading("Lý do tối đa 500 ký tự");
    }

    private static void badReading(String messageVi) {
        throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_PUNCH", messageVi);
    }

    private static TimekeepingException unprocessable(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.UNPROCESSABLE_ENTITY, code, messageVi);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
