package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

/** The audience resolves to more than {@link NotificationAudienceResolver#MAX_RECIPIENTS} people; maps to HTTP 422. */
public class AudienceTooLargeException extends ApiException {

    public AudienceTooLargeException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIENCE_TOO_LARGE", "Audience too large",
                "Tối đa 5.000 người nhận mỗi lần gửi");
    }
}
