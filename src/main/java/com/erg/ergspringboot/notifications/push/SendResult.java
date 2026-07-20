package com.erg.ergspringboot.notifications.push;

import java.time.Duration;

public record SendResult(
        boolean success,
        boolean retryable,
        boolean invalidTarget,
        int httpStatus,
        String providerMessageId,
        String errorCode,
        String errorMessage,
        Duration retryAfter,
        long durationMs
) {

    public static SendResult success(int httpStatus, String providerMessageId, long durationMs) {
        return new SendResult(true, false, false, httpStatus, providerMessageId, null, null, null, durationMs);
    }

    public static SendResult failure(
            boolean retryable,
            boolean invalidTarget,
            int httpStatus,
            String errorCode,
            String errorMessage,
            Duration retryAfter,
            long durationMs
    ) {
        return new SendResult(false, retryable, invalidTarget, httpStatus, null, errorCode, errorMessage, retryAfter, durationMs);
    }
}
