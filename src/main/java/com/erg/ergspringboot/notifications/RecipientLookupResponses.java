package com.erg.ergspringboot.notifications;

import java.util.List;

public final class RecipientLookupResponses {

    private RecipientLookupResponses() {
    }

    /** {@code role}: STUDENT | TEACHER | STAFF. {@code meta}: e.g. "Học sinh · 7A · HS0712". */
    public record RecipientCard(String userId, String displayName, String role, String meta) {
    }

    public record AmbiguousLine(String line, int candidates) {
    }

    public record ResolvePeopleResult(
            List<RecipientCard> matched,
            List<String> notFound,
            List<AmbiguousLine> ambiguous
    ) {
    }
}
