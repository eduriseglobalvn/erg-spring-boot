package com.erg.ergspringboot.calendar;

import java.util.List;

/** One page of the catalog search read: entries beyond the 500-per-list cap of the embedded catalog. */
public record CalendarCatalogSearchResponse(String kind, int page, int size, boolean hasMore, List<Item> items) {

    /** {@code schoolId}, {@code grade} and {@code academicYear} are null for kinds that have none. */
    public record Item(String id, String label, String schoolId, String grade, String academicYear) {}
}
