package com.erg.ergspringboot.timekeeping.web;

/** Route literals of the timekeeping API, spelled once (the photo file path lives on LocalPrivateObjectStorage). */
final class TimekeepingPaths {

    static final String ME = "/api/v1/timekeeping/me";
    static final String REQUESTS = "/api/v1/timekeeping/requests";
    static final String ADMIN = "/api/v1/timekeeping/admin";
    static final String APPROVALS = "/api/v1/timekeeping/approvals";

    private TimekeepingPaths() {}
}
