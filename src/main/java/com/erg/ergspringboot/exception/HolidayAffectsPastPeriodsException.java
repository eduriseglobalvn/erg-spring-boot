package com.erg.ergspringboot.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 HOLIDAY_AFFECTS_PAST_PERIODS: a day off would cancel periods that were already taught. The body carries
 * {@code payload} in {@code data} (dates, taughtPeriods, reportedPeriods, schools) so the client can ask the user
 * to confirm and send the same request again with {@code confirmPast = true}.
 */
public class HolidayAffectsPastPeriodsException extends ApiException {

    public static final String CODE = "HOLIDAY_AFFECTS_PAST_PERIODS";

    private final transient Object payload;

    public HolidayAffectsPastPeriodsException(Object payload) {
        super(HttpStatus.CONFLICT, CODE, "Conflict",
                "Ngày nghỉ này sẽ huỷ các tiết đã dạy. Xác nhận để tiếp tục.");
        this.payload = payload;
    }

    public Object payload() {
        return payload;
    }
}
