package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput.Row;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Regression tests of the review findings: publish serialisation, tenant-scoped ids, label race, version required. */
class TeachingScheduleReviewFixesIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;
    @Autowired CalendarBatchLifecycleUseCase lifecycle;

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

    private Row row(String eventId, String cls, String roomId, String teacherId) {
        return new Row("r1", eventId, 3, 1, cls, roomId, null, teacherId, null, null);
    }

    private TeachingScheduleDraftInput input(String batchId, Integer version, String schoolId, String subjectId, Row... rows) {
        return new TeachingScheduleDraftInput(batchId, schoolId, subjectId, null, "2026-11-16", "2026-11-22", template, version,
                List.of(rows));
    }

    private void expectCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TeachingScheduleException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    @Test
    void twoDifferentBatchesWithTheSameSlotCannotBothBePublishedConcurrently() throws Exception {
        setUp();
        String otherClass = classroom(tenant, school, "9A5");
        String b1 = batch(tenant, school, "DRAFT", "Đợt 1");
        String b2 = batch(tenant, school, "DRAFT", "Đợt 2");
        // same teacher, same minute, different classes: only the published one counts for the engine
        event(tenant, b1, school, classId, subject, "DRAFT", OffsetDateTime.parse("2026-11-18T06:30:00Z"), 6, teacher, null, null);
        event(tenant, b2, school, otherClass, subject, "DRAFT", OffsetDateTime.parse("2026-11-18T06:30:00Z"), 6, teacher, null, null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (String batchId : List.of(b1, b2)) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    lifecycle.publish(tenant, batchId, admin);
                    return true;
                } catch (TeachingScheduleException expected) {
                    return false;
                }
            }));
        }
        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) if (result.get()) succeeded++;
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from teaching_schedule_events where tenant_id = ? and status = 'PUBLISHED'",
                Integer.class, tenant)).isEqualTo(1);
    }

    @Test
    void idsFromAnotherTenantOrSchoolAreRejectedWithADomainError() {
        setUp();
        String otherTenant = id();
        String foreignSchool = school(otherTenant, "Trường khác");
        String foreignClass = classroom(otherTenant, foreignSchool, "1A");
        String foreignRoom = room(otherTenant, foreignSchool, "P1");
        String foreignTeacher = user(otherTenant, "Người lạ");
        String foreignSubject = subject(otherTenant, "Văn");
        String sameTenantOtherSchool = school(tenant, "THCS Văn Lang");
        String classOfOtherSchool = classroom(tenant, sameTenantOtherSchool, "7A");

        expectCode(() -> schedule.saveDraft(tenant, input(null, null, school, subject, row(null, foreignClass, null, teacher)), admin),
                "CLASS_INVALID");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, school, subject, row(null, classOfOtherSchool, null, teacher)), admin),
                "CLASS_INVALID");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, school, subject, row(null, null, null, teacher)), admin),
                "CLASS_INVALID");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, school, subject, row(null, classId, null, foreignTeacher)), admin),
                "TEACHER_INVALID");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, school, subject, row(null, classId, foreignRoom, teacher)), admin),
                "ROOM_INVALID");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, school, foreignSubject, row(null, classId, null, teacher)), admin),
                "SUBJECT_INVALID");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, foreignSchool, subject), admin), "NOT_FOUND");
        expectCode(() -> schedule.saveDraft(tenant, input(null, null, null, subject), admin), "SCHOOL_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from teaching_schedule_events where tenant_id = ?", Integer.class, tenant)).isZero();
    }

    @Test
    void anExistingDraftNeedsTheVersionAndOnlyItsOwnEventIds() {
        setUp();
        DraftResultResponse first = schedule.saveDraft(tenant, input(null, null, school, subject, row(null, classId, null, teacher)), admin);
        String batchId = first.batch().id();
        String ownEvent = first.rowIdMap().get(0).eventId();

        expectCode(() -> schedule.saveDraft(tenant, input(batchId, null, school, subject, row(ownEvent, classId, null, teacher)), admin),
                "EXPECTED_VERSION_REQUIRED");

        // an event of another batch (or tenant) must not be overwritten through a client supplied eventId
        String otherBatch = batch(tenant, school, "DRAFT", "Đợt khác");
        String foreignEvent = event(tenant, otherBatch, school, classId, subject, "DRAFT", OffsetDateTime.parse("2026-11-19T06:30:00Z"),
                6, teacher, null, null);
        expectCode(() -> schedule.saveDraft(tenant, input(batchId, first.version(), school, subject,
                row(foreignEvent, classId, null, teacher)), admin), "EVENT_NOT_IN_BATCH");
        assertThat(jdbc.queryForObject("select batch_id from teaching_schedule_events where id = ?", String.class, foreignEvent))
                .isEqualTo(otherBatch);

        assertThat(schedule.saveDraft(tenant, input(batchId, first.version(), school, subject, row(ownEvent, classId, null, teacher)),
                admin).rowIdMap().get(0).eventId()).isEqualTo(ownEvent);
    }

    @Test
    void parallelNewBatchesOfOneSchoolGetDistinctLabels() throws Exception {
        setUp();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return schedule.saveDraft(tenant, input(null, null, school, subject), admin).batch().label();
            }));
        }
        go.countDown();
        List<String> labels = new ArrayList<>();
        for (Future<String> result : results) labels.add(result.get());
        pool.shutdown();

        assertThat(labels).containsExactlyInAnyOrder("Đợt 1", "Đợt 2");
    }

    @Test
    void overviewConflictsCarryNoFixSuggestionButTheDraftOnesDo() {
        setUp();
        String spare = instructor(tenant, "Dự phòng", school);
        String busy = instructor(tenant, "Giáo viên A", school);
        String otherClass = classroom(tenant, school, "9A5");
        String published = batch(tenant, school, "PUBLISHED", "Đợt cũ");
        // Wed 18/11/2026 07:15 Vietnam time, period 1
        event(tenant, published, school, otherClass, subject, "PUBLISHED", at("2026-11-18", "07:15"), 1, busy, null, null);
        DraftResultResponse saved = schedule.saveDraft(tenant, input(null, null, school, subject, row(null, classId, null, busy)), admin);

        assertThat(saved.conflicts()).isNotEmpty();
        assertThat(saved.conflicts()).anyMatch(c -> c.fix() != null && spare.equals(c.fix().teacherId()));
        var grid = schedule.overviewGrid(tenant, java.time.LocalDate.parse("2026-11-16"), List.of(school));
        assertThat(grid.conflicts()).isNotEmpty().allMatch(c -> c.fix() == null);
    }
}
