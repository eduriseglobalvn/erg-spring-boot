package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.exception.ApiException;
import org.springframework.http.HttpStatus;

/** The audience resolves to more than the allowed number of people; maps to HTTP 422. */
public class AudienceTooLargeException extends ApiException {

    public AudienceTooLargeException() {
        this(NotificationAudienceResolver.MAX_RECIPIENTS);
    }

    public AudienceTooLargeException(int max) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIENCE_TOO_LARGE", "Audience too large",
                "Tối đa " + String.format(java.util.Locale.ROOT, "%,d", max).replace(',', '.') + " người nhận mỗi lần gửi");
    }
}
