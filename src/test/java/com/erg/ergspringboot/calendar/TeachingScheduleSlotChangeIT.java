package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotChangeResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Edit;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Scope;
import com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Swap;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Edit and teacher swap of PUBLISHED periods: scope, conflicts, versions, audit, events and tenant isolation. Clock: Thu 12/11/2026. */
@RecordApplicationEvents
class TeachingScheduleSlotChangeIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;
    @Autowired ApplicationEvents recorded;
    @Autowired CalendarHolidayPort holidayPort;

    private String tenant;
    private String school;
    private String classId;
    private String subject;
    private String teacher;
    private String substitute;
    private String admin;
    private String batch;
    private List<String> wednesdays;

    /** Wednesdays 18/11, 25/11 and 2/12 at period 6 (13:30), taught by {@code teacher}, in a published batch. */
    private void setUp() {
        tenant = id();
        school = school(tenant, "THCS Trương Công Định");
        classId = classroom(tenant, school, "9A4");
        subject = subject(tenant, "Tin học");
        teacher = user(tenant, "Q.Vương");
        substitute = user(tenant, "T.Tuấn");
        admin = user(tenant, "Giáo vụ");
        schedule.periodTemplates(tenant);
        batch = batch(tenant, school, "PUBLISHED", "Đợt HK1·2");
        wednesdays = new ArrayList<>();
        for (String day : List.of("2026-11-18", "2026-11-25", "2026-12-02")) {
            wednesdays.add(event(tenant, batch, school, classId, subject, "PUBLISHED", at(day, "13:30"), 6, teacher, null, null));
        }
    }

    private static Scope scope(String kind, String... dates) {
        return new Scope(kind, dates.length == 0 ? null : List.of(dates));
    }

    private Edit edit(String eventId, Scope scope, Integer periodNo, boolean dry) {
        return new Edit(eventId, null, scope, dry, periodNo, null, null, null, null, null, "Đổi tiết");
    }

    private Swap swap(String eventId, Scope scope, String newTeacher, boolean dry) {
        return new Swap(eventId, null, scope, newTeacher, "TIME_CONFLICT", null, dry);
    }

    private String startOf(String eventId) {
        return jdbc.queryForObject("select to_char(start_at at time zone 'Asia/Ho_Chi_Minh', 'HH24:MI') from teaching_schedule_events where id = ?",
                String.class, eventId);
    }

    private String mainOf(String eventId) {
        return jdbc.queryForObject("select teacher_user_id from teaching_schedule_event_teachers where event_id = ? and role = 'MAIN'",
                String.class, eventId);
    }

    private long version(String eventId) {
        return jdbc.queryForObject("select record_version from teaching_schedule_events where id = ?", Long.class, eventId);
    }

    private void expectCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TeachingScheduleException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    @Test
    void thisOnlyMovesOnePeriodBumpsItsVersionAuditsAndPublishesOneEvent() {
        setUp();
        long before = version(wednesdays.get(0));

        SlotChangeResponse result = schedule.editSlot(tenant, edit(wednesdays.get(0), scope("THIS_ONLY"), 7, false), admin);

        assertThat(result.applied()).isTrue();
        assertThat(result.affectedCount()).isEqualTo(1);
        assertThat(startOf(wednesdays.get(0))).isEqualTo("14:15");
        assertThat(startOf(wednesdays.get(1))).isEqualTo("13:30");
        assertThat(version(wednesdays.get(0))).isGreaterThan(before);
        assertThat(result.events().get(0).version()).isEqualTo((int) version(wednesdays.get(0)));
        assertThat(jdbc.queryForObject("select period_start from teaching_schedule_events where id = ?", Integer.class, wednesdays.get(0)))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where tenant_id = ? and action = 'SCHEDULE_SLOT_EDITED'"
                + " and resource_id = ?", Integer.class, tenant, wednesdays.get(0))).isEqualTo(1);
        assertThat(recorded.stream(CalendarSlotsChanged.class)).hasSize(1);
        assertThat(recorded.stream(CalendarSlotsChanged.class).findFirst().orElseThrow().calendarEventIds())
                .containsExactly(wednesdays.get(0));
    }

    @Test
    void fromDateToEndAndSelectedDatesReachTheRightRepeats() {
        setUp();

        SlotChangeResponse rest = schedule.editSlot(tenant, edit(wednesdays.get(1), scope("FROM_DATE_TO_END"), 7, false), admin);
        assertThat(rest.affectedCount()).isEqualTo(2);
        assertThat(startOf(wednesdays.get(0))).isEqualTo("13:30");
        assertThat(startOf(wednesdays.get(1))).isEqualTo("14:15");
        assertThat(startOf(wednesdays.get(2))).isEqualTo("14:15");

        SlotChangeResponse chosen = schedule.editSlot(tenant, edit(wednesdays.get(1), scope("SELECTED_DATES", "2026-12-02"), 8, false), admin);
        assertThat(chosen.affectedCount()).isEqualTo(1);
        assertThat(startOf(wednesdays.get(2))).isEqualTo("15:30");
        assertThat(startOf(wednesdays.get(1))).isEqualTo("14:15");
        expectCode(() -> schedule.editSlot(tenant, edit(wednesdays.get(0), scope("SELECTED_DATES", "2026-11-19"), 8, false), admin),
                "DATE_HAS_NO_PERIOD");
    }

    @Test
    void anotherDateIsOnlyForASinglePeriodAndNeverASundayOrHoliday() {
        setUp();
        Edit toThursday = new Edit(wednesdays.get(0), null, scope("THIS_ONLY"), false, null, "2026-11-19", null, null, null, null, null);
        schedule.editSlot(tenant, toThursday, admin);
        assertThat(jdbc.queryForObject("select weekday from teaching_schedule_events where id = ?", Integer.class, wednesdays.get(0))).isEqualTo(4);

        expectCode(() -> schedule.editSlot(tenant, new Edit(wednesdays.get(1), null, scope("FROM_DATE_TO_END"), false, null, "2026-11-26",
                null, null, null, null, null), admin), "DATE_CHANGE_NEEDS_SINGLE_SCOPE");
        expectCode(() -> schedule.editSlot(tenant, new Edit(wednesdays.get(1), null, scope("THIS_ONLY"), false, null, "2026-11-29",
                null, null, null, null, null), admin), "INVALID_WEEKDAY");
        write("insert into tk_holidays (id, tenant_id, holiday_date, name) values (?, ?, date '2026-11-27', 'Nghỉ')",
                id(), tenant);
        expectCode(() -> schedule.editSlot(tenant, new Edit(wednesdays.get(1), null, scope("THIS_ONLY"), false, null, "2026-11-27",
                null, null, null, null, null), admin), "DATE_IS_HOLIDAY");
        expectCode(() -> schedule.editSlot(tenant, edit(wednesdays.get(1), scope("THIS_ONLY"), null, false), admin), "NOTHING_TO_CHANGE");
    }

    @Test
    void roomAndAssistantChangeAndTheOldAssistantIsToldItLostThePeriod() {
        setUp();
        String roomId = room(tenant, school, "PM 03");
        String assistant = user(tenant, "Trợ giảng A");
        Edit change = new Edit(wednesdays.get(0), null, scope("THIS_ONLY"), false, null, null, roomId, null, assistant, null, null);

        schedule.editSlot(tenant, change, admin);

        assertThat(jdbc.queryForObject("select room_name from teaching_schedule_events where id = ?", String.class, wednesdays.get(0)))
                .isEqualTo("PM 03");
        assertThat(jdbc.queryForObject("select teacher_user_id from teaching_schedule_event_teachers where event_id = ? and role = 'ASSISTANT'",
                String.class, wednesdays.get(0))).isEqualTo(assistant);

        schedule.editSlot(tenant, new Edit(wednesdays.get(0), null, scope("THIS_ONLY"), false, null, null, null, null, null, true, null), admin);
        assertThat(jdbc.queryForObject("select count(*) from teaching_schedule_event_teachers where event_id = ? and role = 'ASSISTANT'",
                Integer.class, wednesdays.get(0))).isZero();
        CalendarSlotsChanged last = recorded.stream(CalendarSlotsChanged.class).reduce((a, b) -> b).orElseThrow();
        assertThat(last.removed()).extracting(CalendarSlotsChanged.RemovedPerson::userId).containsExactly(assistant);
        expectCode(() -> schedule.editSlot(tenant, new Edit(wednesdays.get(0), null, scope("THIS_ONLY"), false, null, null, "nope", null, null, null, null),
                admin), "ROOM_INVALID");
    }

    @Test
    void aBusyTeacherBlocksTheChangeButADryRunOnlyReportsIt() {
        setUp();
        String otherClass = classroom(tenant, school, "9A5");
        event(tenant, batch, school, otherClass, subject, "PUBLISHED", at("2026-11-18", "13:30"), 6, substitute, null, null);

        SlotChangeResponse dry = schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("THIS_ONLY"), substitute, true), admin);
        assertThat(dry.applied()).isFalse();
        assertThat(dry.blocking()).isTrue();
        assertThat(dry.conflicts()).extracting(c -> c.kind()).contains("TEACHER_TIME");
        assertThat(mainOf(wednesdays.get(0))).isEqualTo(teacher);

        assertThatThrownBy(() -> schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("THIS_ONLY"), substitute, false), admin))
                .isInstanceOfSatisfying(TeachingScheduleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("CHANGE_BLOCKED_BY_CONFLICTS");
                    assertThat(error.extensions()).containsEntry("conflictCount", 1);
                });
        assertThat(mainOf(wednesdays.get(0))).isEqualTo(teacher);
        assertThat(recorded.stream(CalendarSlotsChanged.class)).isEmpty();
    }

    @Test
    void aTeacherWhoCannotTravelInTimeIsRefused() {
        setUp();
        String far = school(tenant, "THCS Nguyễn Du");
        String farClass = classroom(tenant, far, "8A1");
        String farBatch = batch(tenant, far, "PUBLISHED", "Đợt 1");
        // ends 13:15, the swapped period starts 13:30: 15 minutes against the default 50
        event(tenant, farBatch, far, farClass, subject, "PUBLISHED", at("2026-11-18", "12:30"), 5, substitute, null, null);

        SlotChangeResponse dry = schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("THIS_ONLY"), substitute, true), admin);

        assertThat(dry.blocking()).isTrue();
        assertThat(dry.conflicts()).extracting(c -> c.kind()).contains("TRAVEL_TIME");
    }

    @Test
    void swapReplacesTheMainTeacherForTheScopeAndTellsTheOldOne() {
        setUp();

        SlotChangeResponse result = schedule.swapTeacher(tenant, swap(wednesdays.get(1), scope("FROM_DATE_TO_END"), substitute, false), admin);

        assertThat(result.affectedCount()).isEqualTo(2);
        assertThat(mainOf(wednesdays.get(0))).isEqualTo(teacher);
        assertThat(mainOf(wednesdays.get(1))).isEqualTo(substitute);
        assertThat(mainOf(wednesdays.get(2))).isEqualTo(substitute);
        CalendarSlotsChanged event = recorded.stream(CalendarSlotsChanged.class).findFirst().orElseThrow();
        assertThat(event.kind()).isEqualTo("SWAP");
        assertThat(event.removed()).extracting(CalendarSlotsChanged.RemovedPerson::userId).containsOnly(teacher);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where tenant_id = ? and action = 'SCHEDULE_TEACHER_SWAPPED'",
                Integer.class, tenant)).isEqualTo(1);
    }

    @Test
    void anEarlierSubstitutionSurvivesAFromDateToEndSwapOfTheOriginalTeacher() {
        setUp();
        String third = user(tenant, "M.Châu");
        schedule.swapTeacher(tenant, swap(wednesdays.get(2), scope("THIS_ONLY"), substitute, false), admin);

        schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("FROM_DATE_TO_END"), third, false), admin);

        assertThat(mainOf(wednesdays.get(0))).isEqualTo(third);
        assertThat(mainOf(wednesdays.get(1))).isEqualTo(third);
        assertThat(mainOf(wednesdays.get(2))).isEqualTo(substitute);
    }

    @Test
    void theAssistantCannotBecomeTheMainTeacherOfTheSamePeriod() {
        setUp();
        String assistant = user(tenant, "Trợ giảng A");
        link(tenant, wednesdays.get(0), assistant, "ASSISTANT");

        expectCode(() -> schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("THIS_ONLY"), assistant, false), admin),
                "ASSISTANT_SAME_AS_TEACHER");
        expectCode(() -> schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("THIS_ONLY"), teacher, false), admin), "SAME_TEACHER");
        expectCode(() -> schedule.swapTeacher(tenant, swap(wednesdays.get(0), scope("THIS_ONLY"), id(), false), admin), "TEACHER_INVALID");
    }

    @Test
    void draftStartedAndCancelledPeriodsAndForeignTenantsAreRefused() {
        setUp();
        String draftBatch = batch(tenant, school, "DRAFT", "Đợt 2");
        String draft = event(tenant, draftBatch, school, classId, subject, "DRAFT", at("2026-11-19", "07:15"), 1, teacher, null, null);
        String started = event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-11", "13:30"), 6, teacher, null, null);
        String cancelled = event(tenant, batch, school, classId, subject, "CANCELLED", at("2026-11-20", "13:30"), 6, teacher, null, null);

        expectCode(() -> schedule.editSlot(tenant, edit(draft, scope("THIS_ONLY"), 2, false), admin), "EVENT_NOT_PUBLISHED");
        expectCode(() -> schedule.editSlot(tenant, edit(started, scope("THIS_ONLY"), 2, false), admin), "EVENT_ALREADY_STARTED");
        expectCode(() -> schedule.swapTeacher(tenant, swap(cancelled, scope("THIS_ONLY"), substitute, false), admin), "EVENT_CANCELLED");
        expectCode(() -> schedule.editSlot(id(), edit(wednesdays.get(0), scope("THIS_ONLY"), 7, false), admin), "NOT_FOUND");
        expectCode(() -> schedule.swapTeacher(id(), swap(wednesdays.get(0), scope("THIS_ONLY"), substitute, false), admin), "NOT_FOUND");
        assertThat(startOf(wednesdays.get(0))).isEqualTo("13:30");
    }

    @Test
    void aStaleEventVersionIsRejectedAndEvenWithoutOneOnlyOneOfTwoRacingSwapsWins() throws Exception {
        setUp();
        String third = user(tenant, "M.Châu");
        long seen = version(wednesdays.get(0));
        expectCode(() -> schedule.swapTeacher(tenant, new Swap(wednesdays.get(0), (int) seen + 5, scope("THIS_ONLY"), substitute, null, null, false),
                admin), "STALE_VERSION");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<String>> racers = List.of(
                    () -> race(new Swap(wednesdays.get(0), (int) seen, scope("THIS_ONLY"), substitute, null, null, false)),
                    () -> race(new Swap(wednesdays.get(0), (int) seen, scope("THIS_ONLY"), third, null, null, false)));
            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : pool.invokeAll(racers)) outcomes.add(f.get());
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "STALE_VERSION");
        } finally {
            pool.shutdownNow();
        }
        assertThat(recorded.stream(CalendarSlotsChanged.class)).hasSize(1);
    }

    private String race(Swap swap) {
        try {
            schedule.swapTeacher(tenant, swap, admin);
            return "OK";
        } catch (TeachingScheduleException e) {
            return e.getCode();
        }
    }

    @Test
    void movingIntoARoomAnotherPublishedPeriodHoldsIsBlocked() {
        setUp();
        String roomId = room(tenant, school, "PM 03");
        String otherClass = classroom(tenant, school, "9A5");
        String other = user(tenant, "Khác");
        event(tenant, batch, school, otherClass, subject, "PUBLISHED", at("2026-11-18", "13:30"), 6, other, null, roomId);

        Edit toRoom = new Edit(wednesdays.get(0), null, scope("THIS_ONLY"), true, null, null, roomId, null, null, null, null);
        SlotChangeResponse dry = schedule.editSlot(tenant, toRoom, admin);

        assertThat(dry.blocking()).isTrue();
        assertThat(dry.conflicts()).extracting(c -> c.kind()).contains("ROOM_TIME");
    }

    @Test
    void movingADoublePeriodKeepsItsLengthAndAnUnconfiguredSecondSlotIsRejected() {
        setUp();
        String first = wednesdays.get(0);
        write("update teaching_schedule_events set period_count = 2, end_at = start_at + interval '90 minutes' where id = ?", first);

        schedule.editSlot(tenant, edit(first, scope("THIS_ONLY"), 7, false), admin);

        // periods 7 and 8 are 14:15-15:00 and 15:30-16:15: the event ends with its last period, not with slot 7
        assertThat(jdbc.queryForObject("select to_char(end_at at time zone 'Asia/Ho_Chi_Minh', 'HH24:MI') from teaching_schedule_events"
                + " where id = ?", String.class, first)).isEqualTo("16:15");
        expectCode(() -> schedule.editSlot(tenant, edit(first, scope("THIS_ONLY"), 9, false), admin), "PERIOD_HAS_NO_TIME");
    }

    @Test
    void deletingAHolidayKeepsPeriodsThatCollideOrAreStillCoveredCancelled() {
        setUp();
        String ending = id();
        String other = id();
        write("insert into tk_holidays (id, tenant_id, holiday_date, name) values (?, ?, date '2026-11-18', 'Nghỉ A')", ending, tenant);
        write("insert into tk_holidays (id, tenant_id, holiday_date, name) values (?, ?, date '2026-12-02', 'Nghỉ B')", other, tenant);
        holidayPort.cancelPeriods(tenant, null, List.of(java.time.LocalDate.parse("2026-11-18"), java.time.LocalDate.parse("2026-11-25"),
                java.time.LocalDate.parse("2026-12-02")), ending, "Nghỉ A", admin);
        // a replacement lesson was placed in the freed 25/11 slot of the same class
        event(tenant, batch, school, classId, subject, "PUBLISHED", at("2026-11-25", "13:30"), 6, substitute, null, null);

        int restored = holidayPort.restorePeriods(tenant, ending, admin);

        assertThat(restored).isEqualTo(1);
        assertThat(status(wednesdays.get(0))).isEqualTo("PUBLISHED");
        assertThat(status(wednesdays.get(1))).isEqualTo("CANCELLED");
        assertThat(status(wednesdays.get(2))).isEqualTo("CANCELLED");
    }

    private String status(String eventId) {
        return jdbc.queryForObject("select status from teaching_schedule_events where id = ?", String.class, eventId);
    }
}
