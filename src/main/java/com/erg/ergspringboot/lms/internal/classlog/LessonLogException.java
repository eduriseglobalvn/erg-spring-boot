package com.erg.ergspringboot.lms.internal.classlog;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Class-log failures with the stable codes of the contract (STALE_VERSION, MONTH_CLOSED, ...). */
public class LessonLogException extends ApiException {

    private LessonLogException(HttpStatus status, String code, String message) {
        super(status, code, status.getReasonPhrase(), message);
    }

    public static LessonLogException staleVersion() {
        return new LessonLogException(HttpStatus.CONFLICT, "STALE_VERSION", "The entry changed since it was read; reload and retry.");
    }

    public static LessonLogException monthClosed() {
        return new LessonLogException(HttpStatus.CONFLICT, "MONTH_CLOSED", "The month of this period is closed.");
    }

    public static LessonLogException periodNotHappened() {
        return new LessonLogException(HttpStatus.CONFLICT, "PERIOD_NOT_HAPPENED", "No period of this week has taken place yet.");
    }

    public static LessonLogException notTeacherOfPeriod() {
        return new LessonLogException(HttpStatus.FORBIDDEN, "NOT_TEACHER_OF_PERIOD", "You do not teach this period.");
    }

    public static LessonLogException actualPeriodOutOfRange(int max) {
        return new LessonLogException(HttpStatus.UNPROCESSABLE_ENTITY, "ACTUAL_PERIOD_OUT_OF_RANGE",
                "The actual period must be between 1 and " + max + ".");
    }
}
