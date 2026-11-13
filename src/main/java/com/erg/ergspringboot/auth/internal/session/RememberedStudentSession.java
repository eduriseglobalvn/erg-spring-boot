package com.erg.ergspringboot.auth.internal.session;

import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Ghi nhớ đăng nhập" for students: a sliding 30-day session, never longer than 90 days after login.
 * Only a student-only account (portals = elearning) that ticked the box gets it; staff keep 8h/12h.
 */
public final class RememberedStudentSession {
    public static final Duration SLIDING_TTL = Duration.ofDays(30);
    static final Duration MAX_AGE = Duration.ofDays(90);

    private RememberedStudentSession() {}

    public static boolean applies(boolean rememberMe, List<String> portals) {
        return rememberMe && List.of("elearning").equals(portals);
    }

    public static boolean isRemembered(AuthSession session) {
        return session.isRememberMe() && session.getCreatedAt() != null;
    }

    /** Extends a remembered session on refresh; other sessions keep their absolute expiry. */
    public static void slide(AuthSession session, OffsetDateTime now) {
        if (!isRemembered(session)) {
            return;
        }
        OffsetDateTime slid = now.plus(SLIDING_TTL);
        OffsetDateTime limit = session.getCreatedAt().plus(MAX_AGE);
        session.setExpiresAt(slid.isBefore(limit) ? slid : limit);
    }
}
