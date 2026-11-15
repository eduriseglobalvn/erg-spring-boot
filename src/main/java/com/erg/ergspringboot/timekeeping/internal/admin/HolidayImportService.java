package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.HolidayKind;
import com.erg.ergspringboot.timekeeping.HolidayPayMode;
import com.erg.ergspringboot.timekeeping.HolidayTeachingAction;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportCommand;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportPreview;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportPreviewRow;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportResult;
import com.erg.ergspringboot.timekeeping.TimekeepingAdminViews.HolidayImportRow;
import com.erg.ergspringboot.timekeeping.internal.admin.HolidayIcsParser.IcsEvent;
import com.erg.ergspringboot.timekeeping.internal.admin.HolidayService.Draft;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a year of Vietnamese public holidays from an iCalendar file (the "Ngày lễ ở Việt Nam" calendar of Google),
 * either uploaded or saved with the application ({@code timekeeping/holidays/vn-<year>.ics} next to an optional
 * {@code vn-<year>.properties} with {@code savedOn=yyyy-MM-dd} and {@code verify=yyyy-MM-dd,...}). It never opens a
 * network connection. Only events whose description is "Ngày lễ" count: commemorations ("Ngày lễ kỷ niệm") are
 * dropped.
 */
@Service
public class HolidayImportService {

    static final String SAVED_DIR = "timekeeping/holidays/";
    static final int MAX_FILE_BYTES = 1_000_000;
    private static final String PUBLIC_HOLIDAY = "ngày lễ";

    private final HolidayService holidays;

    HolidayImportService(HolidayService holidays) {
        this.holidays = holidays;
    }

    /** @param file the uploaded .ics, or null to read the copy saved for {@code year} */
    @Transactional(readOnly = true)
    public HolidayImportPreview preview(String tenantId, int year, byte[] file) {
        if (year < 2000 || year > 2100) throw AdminSupport.bad("INVALID_YEAR", "Năm không hợp lệ");
        String source;
        LocalDate savedOn = null;
        Set<LocalDate> verify = Set.of();
        String text;
        if (file != null) {
            source = "UPLOAD";
            text = decode(file);
        } else {
            byte[] saved = saved(SAVED_DIR + "vn-" + year + ".ics");
            if (saved == null) return new HolidayImportPreview("NONE", null, List.of());
            source = "SAVED";
            text = decode(saved);
            Properties meta = properties(SAVED_DIR + "vn-" + year + ".properties");
            savedOn = date(meta.getProperty("savedOn"));
            verify = new HashSet<>();
            for (String d : meta.getProperty("verify", "").split(",")) {
                LocalDate date = date(d.strip());
                if (date != null) verify.add(date);
            }
        }
        Set<LocalDate> existing = holidays.existingDates(tenantId, year);
        List<HolidayImportPreviewRow> rows = new ArrayList<>();
        for (HolidayImportRow day : days(text, year)) {
            String status = existing.contains(day.date()) ? "EXISTS" : verify.contains(day.date()) ? "VERIFY" : "NEW";
            rows.add(new HolidayImportPreviewRow(day.date(), VietnameseLunarCalendar.format(day.date()), day.name(),
                    day.kind(), status));
        }
        return new HolidayImportPreview(source, savedOn, rows);
    }

    /** Each ticked row becomes PAID with its periods cancelled, through the same path as adding by hand. */
    @Transactional
    public HolidayImportResult importHolidays(String tenantId, String actorId, HolidayImportCommand command) {
        if (command == null || command.rows() == null || command.rows().isEmpty()) {
            throw AdminSupport.bad("ROWS_REQUIRED", "Chọn ít nhất một ngày để nhập");
        }
        TreeMap<LocalDate, Draft> fresh = new TreeMap<>();
        int skipped = 0;
        java.util.Map<Integer, Set<LocalDate>> existingByYear = new HashMap<>();
        for (HolidayImportRow row : command.rows()) {
            if (row == null || row.date() == null || row.kind() == null) {
                throw AdminSupport.bad("INVALID_ROW", "Dòng nhập không hợp lệ");
            }
            String name = AdminSupport.text(row.name(), 255, "NAME_REQUIRED", "Nhập tên ngày nghỉ");
            boolean known = existingByYear
                    .computeIfAbsent(row.date().getYear(), y -> holidays.existingDates(tenantId, y)).contains(row.date());
            if (known || fresh.containsKey(row.date())) {
                skipped++;
            } else {
                fresh.put(row.date(), new Draft(row.date(), name, row.kind(), HolidayPayMode.PAID, HolidayTeachingAction.CANCEL));
            }
        }
        holidays.createAll(tenantId, actorId, new ArrayList<>(fresh.values()), Boolean.TRUE.equals(command.confirmPast()));
        return new HolidayImportResult(fresh.size(), skipped);
    }

    /** One row per day of every public-holiday event of {@code year}, by date. */
    static List<HolidayImportRow> days(String icsText, int year) {
        List<IcsEvent> events;
        try {
            events = HolidayIcsParser.parse(icsText);
        } catch (IllegalArgumentException invalid) {
            throw invalidFile();
        }
        TreeMap<LocalDate, HolidayImportRow> byDate = new TreeMap<>();
        for (IcsEvent event : events) {
            if (!PUBLIC_HOLIDAY.equals(HolidayIcsParser.firstLine(event.description()).toLowerCase(Locale.ROOT))) continue;
            HolidayKind kind = compensatory(event.summary()) ? HolidayKind.COMPENSATORY : HolidayKind.STATE;
            for (LocalDate d = event.start(); d.isBefore(event.end()); d = d.plusDays(1)) {
                if (d.getYear() == year) byDate.putIfAbsent(d, new HolidayImportRow(d, event.summary(), kind));
            }
        }
        return new ArrayList<>(byDate.values());
    }

    /** "Ngày nghỉ ..." (and "Nghỉ bù ..." as the board spells it) are compensatory days. */
    private static boolean compensatory(String summary) {
        String s = summary.toLowerCase(Locale.ROOT);
        return s.startsWith("ngày nghỉ") || s.startsWith("nghỉ bù");
    }

    private static String decode(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw invalidFile();
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    .replace("﻿", "");
        } catch (CharacterCodingException notUtf8) {
            throw invalidFile();
        }
    }

    private static byte[] saved(String resource) {
        try (InputStream in = HolidayImportService.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException unreadable) {
            throw invalidFile();
        }
    }

    private static Properties properties(String resource) {
        Properties meta = new Properties();
        byte[] bytes = saved(resource);
        if (bytes != null) {
            try {
                meta.load(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)));
            } catch (IOException unreadable) {
                throw invalidFile();
            }
        }
        return meta;
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip());
        } catch (java.time.format.DateTimeParseException invalid) {
            return null;
        }
    }

    private static com.erg.ergspringboot.timekeeping.TimekeepingException invalidFile() {
        return new com.erg.ergspringboot.timekeeping.TimekeepingException(HttpStatus.BAD_REQUEST,
                "HOLIDAY_IMPORT_INVALID_FILE", "Không đọc được tệp lịch (.ics)");
    }
}
