package com.erg.ergspringboot.timekeeping;

import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.ExplanationContext;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.LeavePreview;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestPage;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.ClockView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.DayDetailView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.MonthView;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PhotoFile;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PunchResult;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.TodayView;
import com.erg.ergspringboot.timekeeping.internal.day.DayAggregationService;
import com.erg.ergspringboot.timekeeping.internal.evidence.FaceConsentService;
import com.erg.ergspringboot.timekeeping.internal.evidence.LocalPrivateObjectStorage;
import com.erg.ergspringboot.timekeeping.internal.me.SelfViewService;
import com.erg.ergspringboot.timekeeping.internal.punch.PunchService;
import com.erg.ergspringboot.timekeeping.internal.request.ApprovalService;
import com.erg.ergspringboot.timekeeping.internal.request.RequestService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Public facade for timekeeping use cases; the only type controllers and other modules call. No JPA type is exposed.
 * The caller's principal must come from the security context, never from request input.
 */
@Service
public class TimekeepingOperations {

    private final PunchService punches;
    private final SelfViewService views;
    private final DayAggregationService days;
    private final FaceConsentService consents;
    private final ObjectProvider<LocalPrivateObjectStorage> localPhotos;
    private final RequestService requests;
    private final ApprovalService approvals;
    private final Clock clock;

    TimekeepingOperations(PunchService punches, SelfViewService views, DayAggregationService days,
            FaceConsentService consents, ObjectProvider<LocalPrivateObjectStorage> localPhotos, RequestService requests,
            ApprovalService approvals, Clock clock) {
        this.punches = punches;
        this.views = views;
        this.days = days;
        this.consents = consents;
        this.localPhotos = localPhotos;
        this.requests = requests;
        this.approvals = approvals;
        this.clock = clock;
    }

    public ClockView clock() {
        return new ClockView(clock.instant());
    }

    public TodayView today(ErgPrincipal principal) {
        return views.today(principal.tenantId(), principal.userId());
    }

    /**
     * Same key twice gives the same punch. Two requests with one key racing past the replay check: the loser hits
     * the unique constraint, its transaction (and uploaded photo) rolls back, and it returns the winner's punch.
     */
    public PunchResult punch(ErgPrincipal principal, String idempotencyKey, PunchCommand command) {
        try {
            return punches.punch(principal.tenantId(), principal.userId(), idempotencyKey, command);
        } catch (DataIntegrityViolationException race) {
            return punches.replay(principal.tenantId(), principal.userId(), idempotencyKey).orElseThrow(() -> race);
        }
    }

    public void recordFaceConsent(ErgPrincipal principal) {
        consents.recordConsent(principal.tenantId(), principal.userId());
    }

    public MonthView month(ErgPrincipal principal, String period) {
        try {
            return views.month(principal.tenantId(), principal.userId(), YearMonth.parse(period));
        } catch (DateTimeParseException | NullPointerException invalid) {
            throw new TimekeepingException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", "Kỳ phải có dạng yyyy-MM");
        }
    }

    public DayDetailView day(ErgPrincipal principal, LocalDate date) {
        return views.day(principal, date);
    }

    /** Rebuilds one person's day from its punches (and, from T5, its approved requests). */
    public void recomputeDay(String tenantId, String userId, LocalDate date) {
        days.recompute(tenantId, userId, date);
    }

    public RequestView createRequest(ErgPrincipal principal, RequestCommand command) {
        return requests.create(principal.tenantId(), principal.userId(), command);
    }

    public RequestView updateRequest(ErgPrincipal principal, String id, RequestCommand command) {
        return requests.update(principal.tenantId(), principal.userId(), id, command);
    }

    public RequestView submitRequest(ErgPrincipal principal, String id) {
        return requests.submit(principal.tenantId(), principal.userId(), id);
    }

    public RequestView cancelRequest(ErgPrincipal principal, String id) {
        return requests.cancel(principal.tenantId(), principal.userId(), id);
    }

    public RequestView remindRequest(ErgPrincipal principal, String id) {
        return requests.remind(principal.tenantId(), principal.userId(), id);
    }

    public RequestView request(ErgPrincipal principal, String id) {
        return requests.get(principal.tenantId(), principal.userId(), id);
    }

    public RequestPage myRequests(ErgPrincipal principal, RequestStatus status, int page, int size) {
        return requests.mine(principal.tenantId(), principal.userId(), status, page, size);
    }

    public LeavePreview previewLeave(ErgPrincipal principal, LocalDate from, LocalDate to, DayPart part, LeaveKind kind) {
        return requests.previewLeave(principal.tenantId(), principal.userId(), from, to, part, kind);
    }

    public ExplanationContext explanationContext(ErgPrincipal principal, LocalDate date) {
        return requests.explanationContext(principal.tenantId(), principal.userId(), date);
    }

    /** For the approval inbox (T6): {@code actor} is the verified caller; see ApprovalService for the rules. */
    public void approveStep(String tenantId, String requestId, ErgPrincipal actor, boolean onBehalf, String comment) {
        approvals.approveStep(tenantId, requestId, actor, onBehalf, comment);
    }

    public void rejectStep(String tenantId, String requestId, ErgPrincipal actor, boolean onBehalf, String comment) {
        approvals.rejectStep(tenantId, requestId, actor, onBehalf, comment);
    }

    /**
     * Dev-only signed photo file (the local store). 404 when photos live in R2 (its presigned URLs are used
     * directly) or the file is gone; 403 for a bad, tampered or expired signature.
     */
    public PhotoFile signedPhoto(String key, long exp, String sig) {
        LocalPrivateObjectStorage local = localPhotos.getIfAvailable();
        if (local == null) throw new TimekeepingException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy ảnh");
        if (!local.verify(key, exp, sig)) {
            throw new TimekeepingException(HttpStatus.FORBIDDEN, "INVALID_SIGNATURE", "Liên kết ảnh không hợp lệ");
        }
        try {
            return local.read(key).map(p -> new PhotoFile(p.bytes(), p.contentType()))
                    .orElseThrow(() -> new TimekeepingException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy ảnh"));
        } catch (IllegalArgumentException refused) {
            throw new TimekeepingException(HttpStatus.FORBIDDEN, "INVALID_SIGNATURE", "Liên kết ảnh không hợp lệ");
        }
    }
}
