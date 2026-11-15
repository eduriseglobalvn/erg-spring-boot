package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput.Slot;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.PeriodTemplateResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPeriodAssignmentResponse;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** "Giờ tiết của trường": saving tables, dated assignment, the frozen-template rule and tenant isolation. */
class TeachingSchedulePeriodTemplateIT extends TeachingScheduleITBase {

    @Autowired TeachingScheduleOperations schedule;
    @Autowired PeriodTemplateService templates;

    /** The board's THCS table with an evening session; periods 5 and 10 are switched off. */
    private static List<Slot> winterSlots() {
        List<Slot> slots = new ArrayList<>();
        String[][] times = {{"07:15", "08:00"}, {"08:00", "08:45"}, {"09:15", "10:00"}, {"10:00", "10:45"}, {null, null},
                {"13:30", "14:15"}, {"14:15", "15:00"}, {"15:30", "16:15"}, {"16:15", "17:00"}, {null, null},
                {"18:00", "18:45"}, {"18:45", "19:30"}};
        for (int i = 0; i < times.length; i++) {
            slots.add(new Slot(i + 1, i < 5 ? "MORNING" : i < 10 ? "AFTERNOON" : "EVENING", times[i][0], times[i][1]));
        }
        return slots;
    }

    private void expectCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TeachingScheduleException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }

    @Test
    void aSchoolTableAppliesFromItsDateAndTheDefaultCoversEarlierDays() {
        String tenant = id();
        String school = school(tenant, "THCS Trương Công Định");
        String admin = user(tenant, "Giáo vụ");

        SchoolPeriodAssignmentResponse saved = schedule.saveSchoolPeriodTimes(tenant,
                new SchoolPeriodTimesInput(school, "2026-11-01", "Mùa đông", winterSlots()), admin);

        assertThat(saved.effectiveFrom()).isEqualTo("2026-11-01");
        assertThat(saved.current()).isTrue();
        assertThat(saved.templateName()).isEqualTo("Mùa đông");
        PeriodTemplateService.PeriodTemplate winter = templates.templateForSchool(tenant, school, LocalDate.of(2026, 11, 1));
        assertThat(winter.id()).isEqualTo(saved.templateId());
        assertThat(winter.periodsPerDay()).isEqualTo(12);
        assertThat(winter.slot(11).session()).isEqualTo("EVENING");
        assertThat(winter.slot(5).hasTime()).isFalse();
        // before the table starts the school still sees the tenant default
        assertThat(templates.templateForSchool(tenant, school, LocalDate.of(2026, 10, 31)).id()).isNotEqualTo(saved.templateId());
        assertThat(templates.templateForSchool(tenant, school, LocalDate.of(2027, 3, 1)).id()).isEqualTo(saved.templateId());

        List<SchoolPeriodAssignmentResponse> listed = schedule.schoolPeriodAssignments(tenant, List.of(school));
        assertThat(listed).extracting(SchoolPeriodAssignmentResponse::templateId).containsExactly(saved.templateId());
    }

    @Test
    void aLaterTableBecomesCurrentOnlyWhenItsDateArrivesAndTheSameDateReplacesTheChoice() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String admin = user(tenant, "Giáo vụ");
        PeriodTemplateResponse summer = schedule.saveTemplate(tenant, new TeachingPeriodTemplateInput(null, "Mùa hè", winterSlots()));
        schedule.assignTemplate(tenant, school, summer.id(), "2026-09-05", admin);
        // the fixed clock is 12/11/2026: a table from 01/12 is listed but not current
        schedule.saveSchoolPeriodTimes(tenant, new SchoolPeriodTimesInput(school, "2026-12-01", "Học kỳ 2", winterSlots()), admin);

        List<SchoolPeriodAssignmentResponse> listed = schedule.schoolPeriodAssignments(tenant, null);
        assertThat(listed).extracting(SchoolPeriodAssignmentResponse::effectiveFrom).containsExactly("2026-12-01", "2026-09-05");
        assertThat(listed).extracting(SchoolPeriodAssignmentResponse::current).containsExactly(false, true);

        PeriodTemplateResponse other = schedule.saveTemplate(tenant, new TeachingPeriodTemplateInput(null, "Khác", winterSlots()));
        schedule.assignTemplate(tenant, school, other.id(), "2026-09-05", admin);
        assertThat(schedule.schoolPeriodAssignments(tenant, List.of(school))).extracting(SchoolPeriodAssignmentResponse::templateId)
                .containsExactly(listed.get(0).templateId(), other.id());
    }

    @Test
    void anUnusedTemplateIsReplacedInPlaceButOneABatchUsesIsFrozen() {
        String tenant = id();
        String school = school(tenant, "THCS Văn Lang");
        PeriodTemplateResponse created = schedule.saveTemplate(tenant, new TeachingPeriodTemplateInput(null, "Bản 1", winterSlots()));

        List<Slot> shorter = winterSlots().subList(0, 4);
        PeriodTemplateResponse replaced = schedule.saveTemplate(tenant, new TeachingPeriodTemplateInput(created.id(), "Bản 2", shorter));
        assertThat(replaced.id()).isEqualTo(created.id());
        assertThat(replaced.name()).isEqualTo("Bản 2");
        assertThat(replaced.periodsPerDay()).isEqualTo(4);
        assertThat(replaced.slots()).hasSize(4);

        String batch = batch(tenant, school, "DRAFT", "Đợt 1");
        write("update teaching_schedule_batches set period_template_id = ? where id = ?", created.id(), batch);
        expectCode(() -> schedule.saveTemplate(tenant, new TeachingPeriodTemplateInput(created.id(), "Bản 3", shorter)),
                "TEMPLATE_IN_USE");
        // a cancelled batch no longer freezes it
        write("update teaching_schedule_batches set status = 'CANCELLED' where id = ?", batch);
        assertThat(schedule.saveTemplate(tenant, new TeachingPeriodTemplateInput(created.id(), "Bản 3", shorter)).name())
                .isEqualTo("Bản 3");
    }

    @Test
    void anotherTenantCannotReadReplaceOrAssignATemplateOrSchool() {
        String tenantA = id();
        String tenantB = id();
        String schoolA = school(tenantA, "THCS A");
        String schoolB = school(tenantB, "THCS B");
        PeriodTemplateResponse templateA = schedule.saveTemplate(tenantA, new TeachingPeriodTemplateInput(null, "A", winterSlots()));
        PeriodTemplateResponse templateB = schedule.saveTemplate(tenantB, new TeachingPeriodTemplateInput(null, "B", winterSlots()));

        expectCode(() -> schedule.saveTemplate(tenantB, new TeachingPeriodTemplateInput(templateA.id(), "x", winterSlots())), "NOT_FOUND");
        expectCode(() -> schedule.assignTemplate(tenantB, schoolB, templateA.id(), "2026-11-01", null), "NOT_FOUND");
        expectCode(() -> schedule.assignTemplate(tenantB, schoolA, templateB.id(), "2026-11-01", null), "NOT_FOUND");
        expectCode(() -> schedule.saveSchoolPeriodTimes(tenantB, new SchoolPeriodTimesInput(schoolA, "2026-11-01", "x", winterSlots()), null),
                "NOT_FOUND");
        schedule.assignTemplate(tenantA, schoolA, templateA.id(), "2026-11-01", null);
        assertThat(schedule.schoolPeriodAssignments(tenantB, null)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from teaching_period_templates where tenant_id = ? and name = 'x'",
                Integer.class, tenantB)).isZero();
    }
}
