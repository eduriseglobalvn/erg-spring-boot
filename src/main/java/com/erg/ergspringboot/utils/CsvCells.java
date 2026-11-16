package com.erg.ergspringboot.utils;

/** CSV formula-injection guard shared by every CSV export. */
public final class CsvCells {
    private CsvCells() {
    }

    /** Prefixes {@code '} when a spreadsheet would read the cell as a formula. */
    public static String safe(String v) {
        if (v == null || v.isEmpty()) return v;
        char first = v.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + v : v;
    }
}
