package com.erg.ergspringboot.reporting;

import java.util.Locale;

/** Immutable options captured with an asynchronous report job. */
public record ReportExportOptions(
        String format,
        AssignmentProgressFilter filter
) {
    public ReportExportOptions {
        format = format == null || format.isBlank() ? "json" : format.trim().toLowerCase(Locale.ROOT);
        if (filter == null) {
            filter = new AssignmentProgressFilter(null, null, null, null, java.time.ZoneId.of("UTC"), 0, 100);
        }
    }

    public static ReportExportOptions defaults() {
        return new ReportExportOptions("json", null);
    }
}
