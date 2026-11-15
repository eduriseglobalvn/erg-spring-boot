package com.erg.ergspringboot.timekeeping.internal.admin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The slice of iCalendar (RFC 5545) the holiday import needs: all-day VEVENTs with SUMMARY and DESCRIPTION. Lines
 * are unfolded and TEXT escapes undone; anything not understood is ignored. No network, no library.
 */
final class HolidayIcsParser {

    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    private HolidayIcsParser() {}

    /** {@code end} is exclusive (the day after the last day), as in the file; start + 1 when absent. */
    record IcsEvent(LocalDate start, LocalDate end, String summary, String description) {}

    /** @throws IllegalArgumentException when the text is not an iCalendar file */
    static List<IcsEvent> parse(String text) {
        List<String> lines = unfold(text);
        if (lines.stream().noneMatch(l -> l.equalsIgnoreCase("BEGIN:VCALENDAR"))) {
            throw new IllegalArgumentException("not an iCalendar file");
        }
        List<IcsEvent> events = new ArrayList<>();
        boolean inEvent = false;
        LocalDate start = null;
        LocalDate end = null;
        String summary = "";
        String description = "";
        for (String line : lines) {
            if (line.equalsIgnoreCase("BEGIN:VEVENT")) {
                inEvent = true;
                start = null;
                end = null;
                summary = "";
                description = "";
            } else if (line.equalsIgnoreCase("END:VEVENT")) {
                if (inEvent && start != null) {
                    events.add(new IcsEvent(start, end == null ? start.plusDays(1) : end, summary, description));
                }
                inEvent = false;
            } else if (inEvent) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name = line.substring(0, colon).toUpperCase(Locale.ROOT);
                String value = line.substring(colon + 1);
                String property = name.contains(";") ? name.substring(0, name.indexOf(';')) : name;
                switch (property) {
                    case "DTSTART" -> start = date(value);
                    case "DTEND" -> end = date(value);
                    case "SUMMARY" -> summary = unescape(value).strip();
                    case "DESCRIPTION" -> description = unescape(value);
                    default -> { }
                }
            }
        }
        return events;
    }

    /** First line of the description: Google adds a "To hide holidays..." footer on later lines. */
    static String firstLine(String description) {
        int cut = description.indexOf('\n');
        return (cut < 0 ? description : description.substring(0, cut)).strip();
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value.length() > 8 ? value.substring(0, 8) : value, BASIC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static List<String> unfold(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && !lines.isEmpty()) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
            } else if (!raw.isBlank()) {
                lines.add(raw.stripTrailing());
            }
        }
        return lines;
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";")
                .replace("\\\\", "\\");
    }
}
