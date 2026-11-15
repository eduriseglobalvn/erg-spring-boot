package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput.Row;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.BatchResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** B4: draft save, publish gate, cancel of future periods only, rename, and the published-event edit checks. */
@RecordApplicationEvents
class TeachingScheduleLifecycleIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;
    @Autowired CalendarOperations calendar;
    @Autowired CalendarReadPort reads;
    @Autowired ApplicationEvents recorded;

    private String tenant;
    private String school;
    private String classId;
    private String subject;
    private String teacher;
    private String admin;
    private String template;

    private void setUp() {
        tenant = id();
        school = school(tenant, "THCS Nguyễn Du");
        classId = classroom(tenant, school, "9A4");
        subject = subject(tenant, "Toán");
        teacher = user(tenant, "Q.Vương");
        admin = user(tenant, "Giáo vụ");
        template = schedule.periodTemplates(tenant).get(0).id();
    }

    private Row row(String clientId, String eventId, int weekday, int period, String teacherId, String assistantId, String roomId,
            String newRoom) {
        return new Row(clientId, eventId, weekday, period, classId, roomId, newRoom, teacherId, assistantId, null);
    }

    private TeachingScheduleDraftInput input(String batchId, Integer version, Row... rows) {
        // Mon 16/11 .. Sun 6/12/2026: a Wednesday row occurs 18/11, 25/11 and 2/12
        return new TeachingScheduleDraftInput(batchId, school, subject, null, "2026-11-16", "2026-12-06", template, version,
                List.of(rows));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private void expectCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TeachingScheduleException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    @Test
    void draftIsCreatedReplacedAndGuardedByTheVersion() {
        setUp();
        String assistant = user(tenant, "Trợ giảng");

        DraftResultResponse first = schedule.saveDraft(tenant,
                input(null, null, row("r1", null, 3, 1, teacher, assistant, null, "Phòng Tin 1")), admin);

        assertThat(first.batch().label()).isEqualTo("Đợt 1");
        assertThat(first.batch().status()).isEqualTo("DRAFT");
        assertThat(first.rowIdMap()).hasSize(1);
        assertThat(first.rowIdMap().get(0).clientRowId()).isEqualTo("r1");
        String batchId = first.batch().id();
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and status = 'DRAFT'", batchId)).isEqualTo(3);
        assertThat(count("select count(*) from teaching_schedule_event_teachers where tenant_id = ? and role = 'ASSISTANT'", tenant)).isEqualTo(3);
        assertThat(count("select count(*) from teaching_schedule_rooms where school_id = ? and name = 'Phòng Tin 1'", school)).isEqualTo(1);

        // 2nd save: the same room typed in other case reuses it, the first occurrence keeps its id, the version moves on
        String keptId = first.rowIdMap().get(0).eventId();
        DraftResultResponse second = schedule.saveDraft(tenant, input(batchId, first.version(),
                row("r1", keptId, 3, 1, teacher, null, null, "phòng tin 1")), admin);
        assertThat(second.version()).isGreaterThan(first.version());
        assertThat(second.rowIdMap().get(0).eventId()).isEqualTo(keptId);
        assertThat(count("select count(*) from teaching_schedule_rooms where school_id = ?", school)).isEqualTo(1);
        assertThat(count("select count(*) from teaching_schedule_event_teachers where tenant_id = ? and role = 'ASSISTANT'", tenant)).isZero();

        // a stale tab is refused, and a row that is no longer sent is deleted for good
        expectCode(() -> schedule.saveDraft(tenant, input(batchId, first.version()), admin), "STALE_VERSION");
        DraftResultResponse emptied = schedule.saveDraft(tenant, input(batchId, second.version()), admin);
        assertThat(emptied.rowIdMap()).isEmpty();
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ?", batchId)).isZero();

        // the next batch of the school gets the next free label
        assertThat(schedule.saveDraft(tenant, input(null, null), admin).batch().label()).isEqualTo("Đợt 2");
        assertThat(schedule.draft(tenant, batchId).batch().id()).isEqualTo(batchId);
    }

    @Test
    void draftRejectsBadInput() {
        setUp();
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, row("r1", null, 3, 1, teacher, teacher, null, null)), admin),
                "ASSISTANT_SAME_AS_TEACHER");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, row("r1", null, 9, 1, teacher, null, null, null)), admin),
                "INVALID_WEEKDAY");
        expectCode(() -> schedule.saveDraft(tenant, new TeachingScheduleDraftInput(null, school, subject, null, "2026-11-16",
                "2027-12-06", template, null, List.of()), admin), "RANGE_TOO_LONG");
        expectCode(() -> schedule.saveDraft(tenant, new TeachingScheduleDraftInput(null, school, null, null, "2026-11-16",
                "2026-12-06", template, null, List.of(row("r1", null, 3, 1, teacher, null, null, null))), admin), "SUBJECT_REQUIRED");
        // period 5 has no clock time in the default template
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, row("r1", null, 3, 5, teacher, null, null, null)), admin),
                "PERIOD_HAS_NO_TIME");
    }

    @Test
    void publishIsBlockedByAConflictThenSucceedsOnceAndFiresTheEventOnce() {
        setUp();
        String otherBatch = batch(tenant, school, "PUBLISHED", "Đợt cũ");
        String otherClass = classroom(tenant, school, "9A5");
        event(tenant, otherBatch, school, otherClass, subject, "PUBLISHED", at("2026-11-18", "07:15"), 1, teacher, null, null);

        DraftResultResponse saved = schedule.saveDraft(tenant,
                input(null, null, row("r1", null, 3, 1, teacher, null, null, null)), admin);
        assertThat(saved.conflicts()).extracting(c -> c.kind()).contains("TEACHER_TIME");

        assertThatThrownBy(() -> schedule.publishBatch(tenant, saved.batch().id(), saved.version(), admin))
                .isInstanceOfSatisfying(TeachingScheduleException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("PUBLISH_BLOCKED_BY_CONFLICTS");
                    assertThat(error.extensions()).containsKey("conflictCount");
                });
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and status = 'DRAFT'",
                saved.batch().id())).isEqualTo(3);

        // move the draft to another teacher: no conflict, publish works
        String free = user(tenant, "Giáo viên khác");
        DraftResultResponse fixed = schedule.saveDraft(tenant, input(saved.batch().id(), saved.version(),
                row("r1", null, 3, 1, free, null, null, null)), admin);
        assertThat(fixed.conflicts()).isEmpty();
        BatchResponse published = schedule.publishBatch(tenant, saved.batch().id(), fixed.version(), admin);

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.affectedEventCount()).isEqualTo(3);
        assertThat(recorded.stream(CalendarBatchPublished.class).filter(e -> e.batchId().equals(saved.batch().id())).count())
                .isEqualTo(1);
        expectCode(() -> schedule.publishBatch(tenant, saved.batch().id(), published.version(), admin), "BATCH_NOT_DRAFT");
        // a published batch is no longer a draft
        expectCode(() -> schedule.saveDraft(tenant, input(saved.batch().id(), published.version()), admin), "BATCH_NOT_DRAFT");
    }

    @Test
    void publishRejectsStaleVersionAndEmptyBatch() {
        setUp();
        DraftResultResponse empty = schedule.saveDraft(tenant, input(null, null), admin);
        expectCode(() -> schedule.publishBatch(tenant, empty.batch().id(), empty.version(), admin), "BATCH_EMPTY");
        expectCode(() -> schedule.publishBatch(tenant, empty.batch().id(), empty.version() + 7, admin), "STALE_VERSION");
        expectCode(() -> schedule.publishBatch(tenant, id(), 0, admin), "NOT_FOUND");
    }

    @Test
    void cancellingAPublishedBatchKeepsPastPeriodsForTimekeeping() {
        setUp();
        String batchId = batch(tenant, school, "PUBLISHED", "Đợt 1");
        event(tenant, batchId, school, classId, subject, "PUBLISHED", at("2026-11-09", "07:15"), 1, teacher, null, null);
        event(tenant, batchId, school, classId, subject, "PUBLISHED", at("2026-11-18", "07:15"), 1, teacher, null, null);
        event(tenant, batchId, school, classId, subject, "PUBLISHED", at("2026-11-25", "07:15"), 1, teacher, null, null);
        int version = schedule.draft(tenant, batchId).batch().version();

        BatchResponse cancelled = schedule.cancelBatch(tenant, batchId, version, admin);

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.affectedEventCount()).isEqualTo(2);
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and status = 'PUBLISHED'", batchId)).isEqualTo(1);
        assertThat(recorded.stream(CalendarBatchCancelled.class).filter(e -> e.batchId().equals(batchId)).toList())
                .singleElement().satisfies(e -> assertThat(e.cancelledEvents()).isEqualTo(2));
        // the past period is still what timekeeping reads; the cancelled future ones are gone
        assertThat(reads.teacherPeriods(tenant, teacher, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-12-31"))).hasSize(1);
        assertThat(reads.teachersWithPeriodStarting(tenant, List.of(teacher), at("2026-11-09", "00:00").toInstant(),
                at("2026-11-10", "00:00").toInstant())).containsExactly(teacher);
        expectCode(() -> schedule.cancelBatch(tenant, batchId, cancelled.version(), admin), "BATCH_ALREADY_CANCELLED");
    }

    @Test
    void cancellingADraftCancelsEveryEvent() {
        setUp();
        DraftResultResponse saved = schedule.saveDraft(tenant,
                input(null, null, row("r1", null, 3, 1, teacher, null, null, null)), admin);

        BatchResponse cancelled = schedule.cancelBatch(tenant, saved.batch().id(), saved.version(), admin);

        assertThat(cancelled.affectedEventCount()).isEqualTo(3);
        assertThat(count("select count(*) from teaching_schedule_events where batch_id = ? and status = 'CANCELLED'",
                saved.batch().id())).isEqualTo(3);
    }

    @Test
    void renameValidatesTheLabelAndFiresNoEvent() {
        setUp();
        String published = batch(tenant, school, "PUBLISHED", "Đợt HK1");
        String other = batch(tenant, school, "DRAFT", "Đợt HK2");
        String cancelled = batch(tenant, school, "CANCELLED", "Đợt cũ");
        int v = schedule.draft(tenant, published).batch().version();
        int before = (int) recorded.stream().count();

        expectCode(() -> schedule.renameBatch(tenant, published, "   ", v), "BATCH_LABEL_EMPTY");
        expectCode(() -> schedule.renameBatch(tenant, published, "x".repeat(81), v), "BATCH_LABEL_TOO_LONG");
        expectCode(() -> schedule.renameBatch(tenant, published, "đợt hk2", v), "BATCH_LABEL_TAKEN");
        expectCode(() -> schedule.renameBatch(tenant, cancelled, "Mới", 0), "BATCH_ALREADY_CANCELLED");
        expectCode(() -> schedule.renameBatch(tenant, published, "Mới", v + 5), "STALE_VERSION");

        BatchResponse renamed = schedule.renameBatch(tenant, published, "  Học kỳ 1  ", v);
        assertThat(renamed.label()).isEqualTo("Học kỳ 1");
        assertThat(renamed.version()).isEqualTo(v + 1);
        assertThat(renamed.status()).isEqualTo("PUBLISHED");
        assertThat(recorded.stream(CalendarBatchPublished.class).count() + recorded.stream(CalendarBatchCancelled.class).count()
                + recorded.stream(CalendarEventChanged.class).count()).isZero();
        assertThat(other).isNotNull();
        assertThat((int) recorded.stream().count()).isGreaterThanOrEqualTo(before);
    }

    @Test
    void publishedEventEditChecksStatusVersionAndRoomConflicts() {
        setUp();
        String batchId = batch(tenant, school, "PUBLISHED", "Đợt 1");
        String roomA = room(tenant, school, "P.101");
        String roomB = room(tenant, school, "P.102");
        String other = classroom(tenant, school, "9A5");
        String otherTeacher = user(tenant, "Giáo viên khác");
        String mine = event(tenant, batchId, school, classId, subject, "PUBLISHED", at("2026-11-18", "07:15"), 1, teacher, null, roomA);
        event(tenant, batchId, school, other, subject, "PUBLISHED", at("2026-11-18", "07:15"), 1, otherTeacher, null, roomB);
        String gone = event(tenant, batchId, school, classId, subject, "CANCELLED", at("2026-11-19", "07:15"), 1, teacher, null, null);

        expectCode(() -> calendar.update(tenant, new CalendarEventUpdateRequest(tenant, gone, null, null, "ghi chú", null), admin),
                "EVENT_CANCELLED");
        expectCode(() -> calendar.update(tenant, new CalendarEventUpdateRequest(tenant, mine, null, null, "ghi chú", 99), admin),
                "STALE_VERSION");
        // roomB is taken by another published period at the same time
        expectCode(() -> calendar.update(tenant, new CalendarEventUpdateRequest(tenant, mine, roomB, "P.102", null, 0), admin),
                "PUBLISH_BLOCKED_BY_CONFLICTS");
        assertThat(jdbc.queryForObject("select room_id from teaching_schedule_events where id = ?", String.class, mine)).isEqualTo(roomA);

        String roomC = room(tenant, school, "P.103");
        calendar.update(tenant, new CalendarEventUpdateRequest(tenant, mine, roomC, "P.103", "ghi chú", 0), admin);
        assertThat(jdbc.queryForObject("select room_id from teaching_schedule_events where id = ?", String.class, mine)).isEqualTo(roomC);
        assertThat(recorded.stream(CalendarEventChanged.class).filter(e -> e.calendarEventId().equals(mine)).count()).isEqualTo(1);
    }
}
