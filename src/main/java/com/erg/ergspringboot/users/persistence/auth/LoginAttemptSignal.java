package com.erg.ergspringboot.users.persistence.auth;

import java.time.OffsetDateTime;

/** The three columns the lockout scan reads; avoids hydrating user agent, IP and device text per row. */
public record LoginAttemptSignal(String result, String reason, OffsetDateTime createdAt) {
}
