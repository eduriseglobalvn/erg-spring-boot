package com.erg.ergspringboot.audit;

import java.util.List;

/** Stable bounded page response; never exposes a JPA page or entity. */
public record AuditPage(
        List<AuditEntry> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public AuditPage {
        items = List.copyOf(items == null ? List.of() : items);
    }
}
