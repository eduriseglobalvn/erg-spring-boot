package com.erg.ergspringboot.auth.internal.session;

import com.erg.ergspringboot.users.persistence.auth.AuthSession;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Ghi nhớ đăng nhập": each refresh slides the expiry, never past a limit counted from login.
 * A student-only account (portals = elearning) slides 30 days within 90; staff, admin included,
 * slide 7 days within 30 (BUG-PR-001, owner rule 19/11/2026). Without the box staff keep 8h/12h.
 */
public final class RememberedSession {
    public static final Duration STUDENT_SLIDING_TTL = Duration.ofDays(30);
    static final Duration STUDENT_MAX_AGE = Duration.ofDays(90);
    public static final Duration STAFF_SLIDING_TTL = Duration.ofDays(7);
    static final Duration STAFF_MAX_AGE = Duration.ofDays(30);

    private RememberedSession() {}

    public static boolean isStudent(List<String> portals) {
        return List.of("elearning").equals(portals);
    }

    public static Duration slidingTtl(AuthSession session) {
        return session.isRememberStaff() ? STAFF_SLIDING_TTL : STUDENT_SLIDING_TTL;
    }

    public static boolean isRemembered(AuthSession session) {
        return session.isRememberMe() && session.getCreatedAt() != null;
    }

    /** Extends a remembered session on refresh; other sessions keep their absolute expiry. */
    public static void slide(AuthSession session, OffsetDateTime now) {
        if (!isRemembered(session)) {
            return;
        }
        OffsetDateTime slid = now.plus(slidingTtl(session));
        OffsetDateTime limit = session.getCreatedAt().plus(session.isRememberStaff() ? STAFF_MAX_AGE : STUDENT_MAX_AGE);
        session.setExpiresAt(slid.isBefore(limit) ? slid : limit);
    }
}
