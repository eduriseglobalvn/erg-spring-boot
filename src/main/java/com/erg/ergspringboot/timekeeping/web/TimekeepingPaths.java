package com.erg.ergspringboot.timekeeping.web;

/** Route literals of the timekeeping API, spelled once (the photo file path lives on LocalPrivateObjectStorage). */
final class TimekeepingPaths {

    static final String ME = "/api/v1/timekeeping/me";
    static final String REQUESTS = "/api/v1/timekeeping/requests";
    static final String ADMIN = "/api/v1/timekeeping/admin";
    static final String APPROVALS = "/api/v1/timekeeping/approvals";

    /** Under {@link #ADMIN}. */
    static final String HOLIDAYS = "/holidays";
    static final String HOLIDAY_BY_ID = "/holidays/{id}";
    static final String HOLIDAY_IMPORT_PREVIEW = "/holidays/import-preview";
    static final String HOLIDAY_IMPORT = "/holidays/import";
    static final String SATURDAY_MODES = "/saturday-modes";
    static final String SATURDAY_MODE_BY_DATE = "/saturday-modes/{date}";

    private TimekeepingPaths() {}
}
