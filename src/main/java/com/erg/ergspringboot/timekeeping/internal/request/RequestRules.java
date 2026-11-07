package com.erg.ergspringboot.timekeeping.internal.request;

import com.erg.ergspringboot.timekeeping.DayPart;
import com.erg.ergspringboot.timekeeping.DayStatus;
import com.erg.ergspringboot.timekeeping.ExplanationReason;
import com.erg.ergspringboot.timekeeping.LeaveKind;
import com.erg.ergspringboot.timekeeping.Missing;
import com.erg.ergspringboot.timekeeping.RequestType;
import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.TimekeepingRequestViews.RequestCommand;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkDay;
import com.erg.ergspringboot.timekeeping.persistence.TkDayRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkPolicy;
import com.erg.ergspringboot.timekeeping.persistence.TkPunchRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkRequest;
import com.erg.ergspringboot.timekeeping.persistence.TkRequestRepository;
import com.erg.ergspringboot.timekeeping.persistence.TkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Request rules. {@link #apply} = shape checks (400) for drafts and submit; {@link #checkForSubmit} = business
 * checks at submit: locked month (409), overlap (409), workdays and annual balance (422), explanation window,
 * missing-punch day, monthly limit and proposed OUT (422). Days are always counted here, never taken from a client.
 */
@Component
class RequestRules {

    static final int MAX_SPAN_DAYS = 31;
    static final int NOTE_MAX = 1000;
    private static final int OUT_WINDOW_MIN = 60;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkContextResolver context;
    private final LeaveBalanceService balance;
    private final TkRequestRepository requests;
    private final TkDayRepository days;
    private final TkPunchRepository punches;
    private final Clock clock;

    RequestRules(WorkContextResolver context, LeaveBalanceService balance, TkRequestRepository requests,
            TkDayRepository days, TkPunchRepository punches, Clock clock) {
        this.context = context;
        this.balance = balance;
        this.requests = requests;
        this.days = days;
        this.punches = punches;
        this.clock = clock;
    }

    /** Copies the command onto the request, keeping only the fields of its type. */
    static void apply(TkRequest r, RequestCommand c) {
        if (c == null || c.type() == null) throw bad("INVALID_TYPE", "Chọn loại đơn");
        String note = c.note() == null || c.note().isBlank() ? null : c.note().strip();
        if (note != null && note.length() > NOTE_MAX) throw bad("NOTE_TOO_LONG", "Ghi chú tối đa 1000 ký tự");
        r.setType(c.type());
        r.setNote(note);
        r.setLeaveKind(null);
        r.setFromDate(null);
        r.setToDate(null);
        r.setPart(DayPart.FULL);
        r.setWorkDate(null);
        r.setProposedOut(null);
        r.setExplanationReason(null);
        r.setDays(null);
        if (c.type() == RequestType.EXPLANATION) {
            if (c.workDate() == null) throw bad("INVALID_DATE", "Chọn ngày giải trình");
            if (c.explanationReason() == null) throw bad("INVALID_REASON", "Chọn lý do thiếu");
            boolean needsOut = c.explanationReason() == ExplanationReason.FORGOT || c.explanationReason() == ExplanationReason.DEVICE;
            if (needsOut && c.proposedOut() == null) throw bad("PROPOSED_OUT_REQUIRED", "Chọn giờ ra đề nghị");
            r.setWorkDate(c.workDate());
            r.setExplanationReason(c.explanationReason());
            r.setProposedOut(c.proposedOut());
            return;
        }
        if (c.fromDate() == null || c.toDate() == null) throw bad("INVALID_DATE", "Chọn ngày bắt đầu và kết thúc");
        if (c.fromDate().isAfter(c.toDate())) throw bad("INVALID_RANGE", "Ngày kết thúc phải sau ngày bắt đầu");
        if (ChronoUnit.DAYS.between(c.fromDate(), c.toDate()) >= MAX_SPAN_DAYS) {
            throw bad("RANGE_TOO_LONG", "Mỗi đơn tối đa 31 ngày");
        }
        r.setFromDate(c.fromDate());
        r.setToDate(c.toDate());
        if (c.type() == RequestType.LEAVE) {
            if (c.leaveKind() == null) throw bad("INVALID_LEAVE_KIND", "Chọn loại nghỉ");
            DayPart part = c.part() == null ? DayPart.FULL : c.part();
            if (part != DayPart.FULL && !c.fromDate().equals(c.toDate())) {
                throw bad("INVALID_PART", "Nghỉ buổi sáng hoặc buổi chiều chỉ áp dụng cho một ngày");
            }
            r.setLeaveKind(c.leaveKind());
            r.setPart(part);
        } else if (note == null) {
            throw bad("NOTE_REQUIRED", "Nhập lý do");
        }
    }

    /** Sets {@code days} (ranged types). The request already passed {@link #apply}. */
    void checkForSubmit(TkRequest r) {
        boolean explanation = r.getType() == RequestType.EXPLANATION;
        LocalDate from = explanation ? r.getWorkDate() : r.getFromDate();
        LocalDate to = explanation ? r.getWorkDate() : r.getToDate();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            context.requireOpen(r.getTenantId(), r.getSchoolId(), m.atDay(1));
        }
        if (requests.findActiveTouching(r.getTenantId(), r.getUserId(), r.getId(), from, to).stream()
                .anyMatch(other -> overlaps(r, other))) {
            throw new TimekeepingException(HttpStatus.CONFLICT, "REQUEST_OVERLAP", "Đã có đơn khác trùng thời gian này");
        }
        if (explanation) {
            checkExplanation(r);
            return;
        }
        BigDecimal counted = balance.countDays(r.getTenantId(), r.getSchoolId(), from, to, r.getPart());
        if (counted.signum() == 0) {
            throw unprocessable("NO_WORKDAYS", "Khoảng ngày đã chọn không có ngày làm việc");
        }
        r.setDays(counted.setScale(1, RoundingMode.HALF_UP));
        if (r.getType() == RequestType.LEAVE && r.getLeaveKind() == LeaveKind.ANNUAL) checkAnnualBalance(r);
    }

    /** Explanations only clash with explanations of the same day; ranges with ranges, except AM vs PM of one day. */
    static boolean overlaps(TkRequest r, TkRequest other) {
        boolean explanation = r.getType() == RequestType.EXPLANATION;
        if (explanation != (other.getType() == RequestType.EXPLANATION)) return false;
        if (explanation) return Objects.equals(r.getWorkDate(), other.getWorkDate());
        boolean halves = r.getPart() != DayPart.FULL && other.getPart() != DayPart.FULL;
        return !(halves && r.getFromDate().equals(other.getFromDate()) && r.getPart() != other.getPart());
    }

    /** Checked per calendar year the leave touches; the available balance already excludes other pending leave. */
    private void checkAnnualBalance(TkRequest r) {
        for (YearBalance year : annualByYear(r.getTenantId(), r.getUserId(), r.getSchoolId(), r.getFromDate(),
                r.getToDate(), r.getPart(), r.getId())) {
            if (year.wanted().compareTo(year.available()) > 0) {
                throw unprocessable("INSUFFICIENT_LEAVE",
                        "Phép năm còn " + vi(year.available()) + " ngày, không đủ cho " + vi(year.wanted()) + " ngày");
            }
        }
    }

    /** Annual leave wanted vs available (other pending leave taken out, floored at 0) per year the range touches. */
    record YearBalance(int year, BigDecimal wanted, BigDecimal available) {}

    List<YearBalance> annualByYear(String tenantId, String userId, String schoolId, LocalDate start, LocalDate end,
            DayPart part, String excludeRequestId) {
        List<YearBalance> years = new ArrayList<>();
        for (int year = start.getYear(); year <= end.getYear(); year++) {
            LocalDate from = max(start, LocalDate.of(year, 1, 1));
            LocalDate to = min(end, LocalDate.of(year, 12, 31));
            BigDecimal wanted = balance.countDays(tenantId, schoolId, from, to, from.equals(to) ? part : DayPart.FULL);
            BigDecimal available = balance.availableAnnual(tenantId, userId, year, excludeRequestId).max(BigDecimal.ZERO);
            years.add(new YearBalance(year, wanted, available));
        }
        return years;
    }

    private void checkExplanation(TkRequest r) {
        LocalDate date = r.getWorkDate();
        TkPolicy policy = context.policyFor(r.getTenantId(), r.getSchoolId(), date);
        LocalDate today = LocalDate.now(clock.withZone(WorkContextResolver.ZONE));
        if (ChronoUnit.DAYS.between(date, today) > policy.getExplanationWindowDays()) {
            throw unprocessable("EXPLANATION_WINDOW",
                    "Chỉ giải trình trong " + policy.getExplanationWindowDays() + " ngày kể từ ngày thiếu");
        }
        TkDay day = days.findByTenantIdAndUserIdAndWorkDate(r.getTenantId(), r.getUserId(), date)
                .filter(d -> d.getStatus() == DayStatus.MISSING_PUNCH)
                .orElseThrow(() -> unprocessable("NOT_MISSING_PUNCH", "Ngày này không thiếu chấm công"));
        // An approved explanation can only stand in for a missing OUT (proposed_out); a missing IN is never repaired.
        if (day.getMissing() != Missing.OUT) {
            throw unprocessable("MISSING_IN_NOT_EXPLAINABLE", "Ngày này thiếu chấm vào nên không giải trình được."
                    + " Hãy gửi đơn nghỉ phép hoặc đơn công tác (làm việc bên ngoài) cho ngày này");
        }
        YearMonth month = YearMonth.from(date);
        long used = requests.countActiveExplanations(r.getTenantId(), r.getUserId(), month.atDay(1), month.atEndOfMonth());
        if (used >= policy.getExplanationsPerMonth()) {
            throw unprocessable("EXPLANATION_LIMIT",
                    "Đã dùng hết " + policy.getExplanationsPerMonth() + " lượt giải trình tháng này");
        }
        // every reason needs the OUT time, else approval would change nothing and still use up a monthly explanation
        if (r.getProposedOut() == null) throw unprocessable("PROPOSED_OUT_REQUIRED", "Chọn giờ ra đề nghị");
        checkProposedOut(r, day);
    }

    /** Inside the OUT punch window (shift start .. shift end + 60 min) and after the IN punch. */
    private void checkProposedOut(TkRequest r, TkDay day) {
        TkShift shift = context.shiftFor(r.getTenantId(), r.getSchoolId(), r.getWorkDate())
                .orElseThrow(() -> unprocessable("NO_SHIFT", "Ngày này không có ca làm việc"));
        LocalTime latest = shift.getEndTime().plusMinutes(OUT_WINDOW_MIN);
        LocalTime earliest = day.getInPunchId() == null ? shift.getStartTime() : punches.findById(day.getInPunchId())
                .map(p -> LocalTime.ofInstant(p.getServerTime(), WorkContextResolver.ZONE))
                .filter(in -> in.isAfter(shift.getStartTime())).orElse(shift.getStartTime());
        LocalTime out = r.getProposedOut();
        if (!out.isAfter(earliest) || out.isAfter(latest)) {
            throw unprocessable("INVALID_PROPOSED_OUT",
                    "Giờ ra đề nghị phải sau giờ vào và không muộn hơn " + latest.format(HH_MM));
        }
    }

    /** 1.5 -> "1,5", 2.0 -> "2". */
    static String vi(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    static TimekeepingException bad(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.BAD_REQUEST, code, messageVi);
    }

    static TimekeepingException unprocessable(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.UNPROCESSABLE_CONTENT, code, messageVi);
    }
}
