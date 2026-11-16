package com.erg.ergspringboot.calendar.internal.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.TeachingScheduleITBase;
import com.erg.ergspringboot.calendar.TeachingScheduleOperations;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.AccessChangeResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.AccessClassChangeResponse;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.Occurrence;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleModel.TeacherRef;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassOptionProjection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** LD B5 on a real database: the 2-week access window per class, homeroom teachers and a cancelled batch. */
class TeachingAccessWindowIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleAccessQuery access;
    @Autowired ClassroomRepository classrooms;
    @Autowired TeachingScheduleOperations schedule;

    @Test
    void classLeftAloneLapsesAfterTwoWeeksWhileTheOtherClassAndTheHomeroomClassStayAndTheDraftNamesTheRevokeDate() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classA = classroom(tenant, school, "9A1");
        String classB = classroom(tenant, school, "9A2");
        String classC = classroom(tenant, school, "9A3");
        String classHome = classroom(tenant, school, "9A4");
        String subject = subject(tenant, "Tin hoc");
        String teacher = user(tenant, "Nguyen Quoc Vuong");
        write("update academic_classes set homeroom_teacher_id = ? where id = ?", teacher, classHome);
        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        // the window starts Monday 00:00 of the week before last (real clock: the live gates read it from the system)
        LocalDate monday = LocalDate.now(VN).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        event(tenant, batch, school, classA, subject, "PUBLISHED", at(monday.minusDays(13).toString(), "08:00"), 1, teacher, null, null);
        event(tenant, batch, school, classC, subject, "PUBLISHED", at(monday.minusDays(15).toString(), "08:00"), 1, teacher, null, null);
        event(tenant, batch, school, classB, subject, "PUBLISHED", at(monday.plusDays(2).toString(), "08:00"), 1, teacher, null, null);

        // A ended inside the window: still held; C ended just before it: lapsed; the homeroom class never lapses
        assertThat(classrooms.findTeacherClassOptions(tenant, teacher)).extracting(ClassOptionProjection::name)
                .containsExactlyInAnyOrder("9A1", "9A2", "9A4");

        // the new draft keeps only class B: A is announced as REVOKE on weekStart(last period) + 21 days
        OffsetDateTime now = OffsetDateTime.now(VN);
        Occurrence draft = new Occurrence("d1", "row1", null, null, null, "DRAFT", school, "THCS", classB, "9A2", null, null,
                "Tin hoc", List.of(new TeacherRef(teacher, "Nguyen Quoc Vuong", "MAIN")), monday.plusDays(9), 2, 1,
                now, now.plusMinutes(45), null, null, now, true);
        AccessChangeResponse change = access.accessChanges(tenant, school, List.of(draft), now).get(0);
        assertThat(change.classes()).extracting(AccessClassChangeResponse::className, AccessClassChangeResponse::state)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("9A2", "EXISTING"),
                        org.assertj.core.groups.Tuple.tuple("9A1", "REVOKE"));
        assertThat(change.classes().stream().filter(c -> "REVOKE".equals(c.state())).findFirst().orElseThrow().revokeOn())
                .isEqualTo(monday.minusDays(13).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(21).toString());
    }

    @Test
    void cancellingAPublishedBatchKeepsAccessUntilTheMondayOfTheLastPastWeekPlus21Days() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyen Du");
        String classA = classroom(tenant, school, "9A1");
        String subject = subject(tenant, "Tin hoc");
        String teacher = user(tenant, "Nguyen Quoc Vuong");
        String admin = user(tenant, "Giao vu");
        String batch = batch(tenant, school, "PUBLISHED", "HK1");
        // fixed clock Thu 12/11/2026: Tuesday 10/11 is past, Friday 27/11 is future and gets cancelled with the batch
        event(tenant, batch, school, classA, subject, "PUBLISHED", at("2026-11-10", "08:00"), 1, teacher, null, null);
        event(tenant, batch, school, classA, subject, "PUBLISHED", at("2026-11-27", "08:00"), 1, teacher, null, null);

        schedule.cancelBatch(tenant, batch, schedule.draft(tenant, batch).batch().version(), admin);

        // weekStart(10/11) = Mon 9/11, +21 days = Mon 30/11: held through Sun 29/11 23:59, gone at 30/11 00:00
        var last = access.held(tenant, List.of(teacher), at("2026-11-29", "23:59"));
        assertThat(last).hasSize(1);
        assertThat(last.get(0).revokeOn()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(access.held(tenant, List.of(teacher), at("2026-11-30", "00:00"))).isEmpty();
    }
}
