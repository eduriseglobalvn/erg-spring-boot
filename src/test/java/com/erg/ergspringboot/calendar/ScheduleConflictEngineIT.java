package com.erg.ergspringboot.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService.PeriodTemplate;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService.DraftRow;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleConflictService.DraftScope;
import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ConflictDetailResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The seven scenarios of KE_HOACH_LICH_DAY.md B3, on a real database. */
class ScheduleConflictEngineIT extends TeachingScheduleITBase {

    @Autowired ScheduleConflictService conflicts;
    @Autowired PeriodTemplateService templates;
    @Autowired TravelTimeService travel;

    private static final LocalDate MONDAY_16_11 = LocalDate.of(2026, 11, 16);

    private DraftScope scope(String tenant, String school, String batch, LocalDate from, LocalDate until) {
        PeriodTemplate template = templates.templateForSchool(tenant, school);
        return new DraftScope(school, batch, "Đợt mới", null, from, until, template);
    }

    private static DraftRow row(String id, int weekday, int period, String classId, String roomId, String teacher) {
        return new DraftRow(id, null, weekday, period, classId, roomId, teacher, null);
    }

    /** Published weekly repeats of one slot, one event per week. */
    private void publishedWeekly(String tenant, String batch, String school, String classId, String subject, String firstDate,
            String time, int period, int weeks, String teacher, String room) {
        for (int w = 0; w < weeks; w++) {
            event(tenant, batch, school, classId, subject, "PUBLISHED", at(LocalDate.parse(firstDate).plusWeeks(w).toString(), time),
                    period, teacher, null, room);
        }
    }

    @Test
    void teacherBusyAtAnotherSchoolIsAnErrorForTenOfTenWeeksWithAFreeTeacherSuggested() {
        String tenant = id();
        String tcd = school(tenant, "THCS Trương Công Định");
        String nguyenDu = school(tenant, "THCS Nguyễn Du");
        String vuong = instructor(tenant, "Nguyễn Quốc Vương", tcd);
        instructor(tenant, "Trần Thanh Tuấn", tcd);
        String math = subject(tenant, "Toán");
        String class9a4 = classroom(tenant, tcd, "9A4");
        String class8a2 = classroom(tenant, nguyenDu, "8A2");
        String published = batch(tenant, nguyenDu, "PUBLISHED", "Đợt HK1");
        publishedWeekly(tenant, published, nguyenDu, class8a2, math, "2026-11-18", "13:30", 6, 10, vuong, null);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, tcd, null, MONDAY_16_11, LocalDate.of(2027, 1, 24)),
                List.of(row("r1", 3, 6, class9a4, null, vuong)));

        assertThat(result).hasSize(1);
        ConflictDetailResponse c = result.get(0);
        assertThat(c.kind()).isEqualTo("TEACHER_TIME");
        assertThat(c.severity()).isEqualTo("ERROR");
        assertThat(c.clientRowId()).isEqualTo("r1");
        assertThat(c.affectedWeeks()).isEqualTo(10);
        assertThat(c.totalWeeks()).isEqualTo(10);
        assertThat(c.firstDate()).isEqualTo("2026-11-18");
        assertThat(c.weekday()).isEqualTo(3);
        assertThat(c.periodNo()).isEqualTo(6);
        assertThat(c.startTime()).isEqualTo("13:30");
        assertThat(c.mine().teacherName()).isEqualTo("Q.Vương");
        assertThat(c.other().className()).isEqualTo("8A2");
        assertThat(c.other().schoolShortName()).isEqualTo("Nguyễn Du");
        assertThat(c.other().batchLabel()).isEqualTo("Đợt HK1");
        assertThat(c.other().batchStatus()).isEqualTo("PUBLISHED");
        assertThat(c.message()).isEqualTo("Q.Vương đang dạy 8A2 ở THCS Nguyễn Du cùng giờ");
        assertThat(c.fix().kind()).isEqualTo("CHANGE_TEACHER");
        assertThat(c.fix().teacherName()).isEqualTo("T.Tuấn");
        assertThat(c.fix().reason()).isEqualTo("FREE");
        assertThat(c.fix().targetClientRowId()).isEqualTo("r1");
        assertThat(c.id()).isEqualTo(conflicts.checkDraft(tenant,
                scope(tenant, tcd, null, MONDAY_16_11, LocalDate.of(2027, 1, 24)),
                List.of(row("r1", 3, 6, class9a4, null, vuong))).get(0).id());
    }

    @Test
    void roomBookedForThreeWeeksIsRoomTimeComparedByIdWithAFreeRoomSuggested() {
        String tenant = id();
        String tcd = school(tenant, "THCS Trương Công Định");
        String math = subject(tenant, "Toán");
        String class9a4 = classroom(tenant, tcd, "9A4");
        String class9a8 = classroom(tenant, tcd, "9A8");
        String pm03 = room(tenant, tcd, "PM 03");
        room(tenant, tcd, "PM 02");
        String published = batch(tenant, tcd, "PUBLISHED", "Đợt HK1");
        publishedWeekly(tenant, published, tcd, class9a8, math, "2026-11-27", "13:30", 6, 3, null, pm03);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, tcd, null, MONDAY_16_11, LocalDate.of(2027, 1, 24)),
                List.of(row("r1", 5, 6, class9a4, pm03, null)));

        assertThat(result).hasSize(1);
        ConflictDetailResponse c = result.get(0);
        assertThat(c.kind()).isEqualTo("ROOM_TIME");
        assertThat(c.severity()).isEqualTo("ERROR");
        assertThat(c.affectedWeeks()).isEqualTo(3);
        assertThat(c.totalWeeks()).isEqualTo(10);
        assertThat(c.firstDate()).isEqualTo("2026-11-27");
        assertThat(c.fix().kind()).isEqualTo("CHANGE_ROOM");
        assertThat(c.fix().roomName()).isEqualTo("PM 02");
    }

    @Test
    void closeSchoolsAreTooFarApartAndTheSchoolOfTheEarlierPeriodOffersATeacher() {
        String tenant = id();
        String vanLang = school(tenant, "THCS Văn Lang");
        String nguyenDu = school(tenant, "THCS Nguyễn Du");
        String lanAnh = instructor(tenant, "Lan Anh", vanLang);
        instructor(tenant, "Thu Trang", vanLang);
        String math = subject(tenant, "Toán");
        String class7a1 = classroom(tenant, vanLang, "7A1");
        String class8a2 = classroom(tenant, nguyenDu, "8A2");
        String published = batch(tenant, vanLang, "PUBLISHED", "Đợt HK1");
        event(tenant, published, vanLang, class7a1, math, "PUBLISHED", at("2026-11-16", "08:00"), 2, lanAnh, null, null);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, nguyenDu, null, MONDAY_16_11, MONDAY_16_11),
                List.of(row("r1", 1, 3, class8a2, null, lanAnh)));

        assertThat(result).hasSize(1);
        ConflictDetailResponse c = result.get(0);
        assertThat(c.kind()).isEqualTo("TRAVEL_TIME");
        assertThat(c.travel().gapMinutes()).isEqualTo(30);
        assertThat(c.travel().requiredMinutes()).isEqualTo(50);
        assertThat(c.travel().previousEndTime()).isEqualTo("08:45");
        assertThat(c.travel().fromSchoolShortName()).isEqualTo("VL");
        assertThat(c.travel().toSchoolShortName()).isEqualTo("ND");
        assertThat(c.other().className()).isEqualTo("7A1");
        assertThat(c.fix().kind()).isEqualTo("CHANGE_PREVIOUS_TEACHER");
        assertThat(c.fix().teacherName()).isEqualTo("Thu Trang");
        assertThat(c.fix().reason()).isEqualTo("SAME_SCHOOL");
        assertThat(c.fix().targetEventId()).isNotNull();
    }

    @Test
    void draftOfAnotherBatchNeverConflicts() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String teacher = instructor(tenant, "Nguyễn Quốc Vương", school);
        String math = subject(tenant, "Toán");
        String classA = classroom(tenant, school, "9A4");
        String classB = classroom(tenant, school, "9A5");
        String room = room(tenant, school, "P1");
        String otherBatch = batch(tenant, school, "DRAFT", "Đợt khác");
        event(tenant, otherBatch, school, classA, math, "DRAFT", at("2026-11-18", "13:30"), 6, teacher, null, room);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, school, null, MONDAY_16_11, MONDAY_16_11.plusDays(6)),
                List.of(row("r1", 3, 6, classB, room, teacher), row("r2", 3, 6, classA, null, null)));

        assertThat(result).isEmpty();
    }

    @Test
    void aPairConfiguredAtTwentyMinutesTolerateAThirtyMinuteGap() {
        String tenant = id();
        String vanLang = school(tenant, "THCS Văn Lang");
        String nguyenDu = school(tenant, "THCS Nguyễn Du");
        String lanAnh = instructor(tenant, "Lan Anh", vanLang);
        String math = subject(tenant, "Toán");
        String class7a1 = classroom(tenant, vanLang, "7A1");
        String class8a2 = classroom(tenant, nguyenDu, "8A2");
        String admin = user(tenant, "Giáo vụ");
        travel.upsert(tenant, vanLang, nguyenDu, 20, admin);
        String published = batch(tenant, vanLang, "PUBLISHED", "Đợt HK1");
        event(tenant, published, vanLang, class7a1, math, "PUBLISHED", at("2026-11-16", "08:00"), 2, lanAnh, null, null);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, nguyenDu, null, MONDAY_16_11, MONDAY_16_11),
                List.of(row("r1", 1, 3, class8a2, null, lanAnh)));

        assertThat(result).isEmpty();
    }

    @Test
    void leaveOnTheAfternoonWarnsForPeriodsSixToTenButNotOneToFive() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String teacher = instructor(tenant, "Nguyễn Quốc Vương", school);
        String classA = classroom(tenant, school, "9A4");
        String classB = classroom(tenant, school, "9A5");
        write("insert into tk_requests (id, tenant_id, school_id, code, user_id, type, leave_kind, from_date, to_date, part, days,"
                + " status, current_step, submitted_at) values (?, ?, ?, ?, ?, 'LEAVE', 'ANNUAL', date '2026-11-16', date '2026-11-20',"
                + " 'PM', 5, 'APPROVED', 1, now())", id(), tenant, school, "DT-" + id().substring(0, 8), teacher);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, school, null, MONDAY_16_11, MONDAY_16_11.plusDays(6)),
                List.of(row("morning", 3, 3, classA, null, teacher), row("afternoon", 3, 7, classB, null, teacher)));

        assertThat(result).hasSize(1);
        ConflictDetailResponse c = result.get(0);
        assertThat(c.kind()).isEqualTo("TEACHER_ON_LEAVE");
        assertThat(c.severity()).isEqualTo("WARNING");
        assertThat(c.clientRowId()).isEqualTo("afternoon");
        assertThat(c.leave().part()).isEqualTo("PM");
        assertThat(c.leave().fromDate()).isEqualTo("2026-11-16");
        assertThat(c.fix()).isNull();
    }

    @Test
    void holidayDatesAreNotGeneratedSoTheyNeitherConflictNorCount() {
        String tenant = id();
        String school = school(tenant, "THCS Nguyễn Du");
        String math = subject(tenant, "Toán");
        String classA = classroom(tenant, school, "9A4");
        write("insert into tk_holidays (id, tenant_id, holiday_date, name) values (?, ?, date '2026-11-20', 'Ngày Nhà giáo Việt Nam')",
                id(), tenant);
        String published = batch(tenant, school, "PUBLISHED", "Đợt HK1");
        publishedWeekly(tenant, published, school, classA, math, "2026-11-20", "07:15", 1, 3, null, null);

        List<ConflictDetailResponse> result = conflicts.checkDraft(tenant,
                scope(tenant, school, null, MONDAY_16_11, LocalDate.of(2026, 12, 6)),
                List.of(row("r1", 5, 1, classA, null, null)));

        assertThat(result).hasSize(1);
        ConflictDetailResponse c = result.get(0);
        assertThat(c.kind()).isEqualTo("CLASS_TIME");
        assertThat(c.firstDate()).isEqualTo("2026-11-27");
        assertThat(c.affectedWeeks()).isEqualTo(2);
        assertThat(c.totalWeeks()).isEqualTo(2);
    }
}
