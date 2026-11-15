package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodSlot;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService;
import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService.TravelTime;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TeachingScheduleSetupIT extends TeachingScheduleITBase {

    @Autowired PeriodTemplateService templates;
    @Autowired TravelTimeService travel;

    @Test
    void defaultTemplateHasTheTenPeriodsOfTheThcsTable() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");

        PeriodTemplate template = templates.templateForSchool(tenant, school);

        assertThat(template.periodsPerDay()).isEqualTo(10);
        assertThat(template.slots()).hasSize(10);
        assertSlot(template, 1, "MORNING", "07:15", "08:00");
        assertSlot(template, 2, "MORNING", "08:00", "08:45");
        assertSlot(template, 3, "MORNING", "09:15", "10:00");
        assertSlot(template, 4, "MORNING", "10:00", "10:45");
        assertSlot(template, 6, "AFTERNOON", "13:30", "14:15");
        assertSlot(template, 7, "AFTERNOON", "14:15", "15:00");
        assertSlot(template, 8, "AFTERNOON", "15:30", "16:15");
        assertSlot(template, 9, "AFTERNOON", "16:15", "17:00");
        assertThat(template.slot(5).hasTime()).isFalse();
        assertThat(template.slot(10).hasTime()).isFalse();
        // asking again reuses the same default instead of seeding a second one
        assertThat(templates.list(tenant)).extracting(PeriodTemplate::id).containsExactly(template.id());
    }

    @Test
    void aSchoolWithItsOwnTemplateUsesItAndAPeriodWithoutTimeCannotBeScheduled() {
        String tenant = id();
        String school = school(tenant, "THCS Văn Lang");
        PeriodTemplate fallback = templates.templateForSchool(tenant, school);
        String own = id();
        write("insert into teaching_period_templates (id, tenant_id, name, periods_per_day) values (?, ?, 'Tiểu học', 6)",
                own, tenant);
        write("insert into teaching_period_template_slots (template_id, period_no, session, start_time, end_time)"
                + " values (?, 1, 'MORNING', time '07:30', time '08:10'), (?, 2, 'MORNING', null, null)", own, own);
        write("insert into school_period_templates (school_id, tenant_id, template_id) values (?, ?, ?)", school, tenant, own);

        PeriodTemplate used = templates.templateForSchool(tenant, school);

        assertThat(used.id()).isEqualTo(own).isNotEqualTo(fallback.id());
        assertThat(templates.slot(used, 1).start()).isEqualTo(LocalTime.of(7, 30));
        assertThatThrownBy(() -> templates.slot(used, 2)).isInstanceOfSatisfying(TeachingScheduleException.class,
                error -> assertThat(error.getCode()).isEqualTo("PERIOD_HAS_NO_TIME"));
    }

    @Test
    void travelTimeIsReadInEitherOrderAndFallsBackToTheConfiguredDefault() {
        String tenant = id();
        String x = school(tenant, "THCS Văn Lang");
        String y = school(tenant, "THCS Nguyễn Du");
        String low = x.compareTo(y) < 0 ? x : y;
        String high = low.equals(x) ? y : x;
        String admin = user(tenant, "Giáo vụ");

        assertThat(travel.requiredMinutes(tenant, x, y)).isEqualTo(50);
        assertThat(travel.requiredMinutes(tenant, x, x)).isZero();

        // the caller passes the pair as (high, low); it is stored once as (low, high)
        TravelTime saved = travel.upsert(tenant, high, low, 20, admin);
        assertThat(saved.schoolA().id()).isEqualTo(low);
        assertThat(travel.requiredMinutes(tenant, low, high)).isEqualTo(20);
        assertThat(travel.requiredMinutes(tenant, high, low)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from school_travel_times where tenant_id = ?", Integer.class, tenant))
                .isEqualTo(1);

        travel.upsert(tenant, low, high, 35, admin);
        assertThat(travel.requiredMinutes(tenant, high, low)).isEqualTo(35);
        assertThat(travel.list(tenant, "văn", 0, 10)).hasSize(1);
        assertThat(travel.list(tenant, "không có", 0, 10)).isEmpty();

        assertThat(travel.delete(tenant, high, low)).isTrue();
        assertThat(travel.requiredMinutes(tenant, x, y)).isEqualTo(50);
        assertThat(travel.delete(tenant, high, low)).isFalse();
    }

    @Test
    void travelTimeRejectsOutOfRangeSamePairAndUnknownSchools() {
        String tenant = id();
        String x = school(tenant, "THCS Văn Lang");
        String y = school(tenant, "THCS Nguyễn Du");

        assertThatThrownBy(() -> travel.upsert(tenant, x, y, 241, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> travel.upsert(tenant, x, x, 10, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> travel.upsert(tenant, x, id(), 10, null)).isInstanceOfSatisfying(
                TeachingScheduleException.class, error -> assertThat(error.getCode()).isEqualTo("NOT_FOUND"));
        // a school of another tenant is not visible
        String foreign = school(id(), "THCS Lạ");
        assertThatThrownBy(() -> travel.upsert(tenant, x, foreign, 10, null))
                .isInstanceOf(TeachingScheduleException.class);
    }

    private static void assertSlot(PeriodTemplate template, int no, String session, String start, String end) {
        PeriodSlot slot = template.slot(no);
        assertThat(slot.session()).isEqualTo(session);
        assertThat(slot.start()).isEqualTo(LocalTime.parse(start));
        assertThat(slot.end()).isEqualTo(LocalTime.parse(end));
    }
}
