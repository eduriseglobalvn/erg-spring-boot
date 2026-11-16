package com.erg.ergspringboot.calendar;

import java.util.List;

/** Short school names for dense schedule UIs (KE_HOACH_LICH_DAY.md 4.5). Pure, no I/O. */
public final class SchoolShortName {

    // Longer prefixes first: "THCS " and "THPT " must win over "TH ".
    private static final List<String> LEVEL_PREFIXES = List.of("THCS ", "THPT ", "TH ", "MN ", "TiH ");
    private static final int KEEP_WHOLE_UP_TO = 10;

    private SchoolShortName() {
    }

    /** Whole name without the level prefix when it fits in 10 characters, otherwise its initials. */
    public static String of(String name) {
        String core = core(name);
        return core.length() <= KEEP_WHOLE_UP_TO ? core : initialsOf(core);
    }

    /** Always initials, used on travel lines ("VL → TCĐ"). */
    public static String initials(String name) {
        return initialsOf(core(name));
    }

    private static String core(String name) {
        String trimmed = name == null ? "" : name.trim();
        for (String prefix : LEVEL_PREFIXES) {
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    private static String initialsOf(String core) {
        StringBuilder initials = new StringBuilder();
        for (String word : core.split("\s+")) {
            if (!word.isEmpty()) initials.append(Character.toUpperCase(word.charAt(0)));
        }
        return initials.toString();
    }
}
