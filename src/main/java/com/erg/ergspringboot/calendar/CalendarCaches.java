package com.erg.ergspringboot.calendar;

/** Names of the caches the calendar module owns besides {@code calendarWorkspace}; evicted together by {@link CalendarCacheEvict}. */
public final class CalendarCaches {

    public static final String WORKSPACE = "calendarWorkspace";
    /** The analysed week of the overview screens: key = tenant, week, school scope. */
    public static final String OVERVIEW_WEEK = "calendarOverviewWeek";
    /** The filter catalog of a workspace read: key = tenant, schools, range. */
    public static final String CATALOG = "calendarCatalog";

    private CalendarCaches() {
    }
}
