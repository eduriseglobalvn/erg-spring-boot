package com.erg.ergspringboot.calendar.internal.graphql;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import com.erg.ergspringboot.calendar.CalendarScopeService;
import com.erg.ergspringboot.calendar.SchoolPeriodTimesInput;
import com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput;
import com.erg.ergspringboot.calendar.TeachingScheduleOperations;
import com.erg.ergspringboot.calendar.TeachingScheduleDraftInput;
import com.erg.ergspringboot.calendar.authorization.CalendarCreate;
import com.erg.ergspringboot.calendar.authorization.CalendarDayLoadRead;
import com.erg.ergspringboot.calendar.authorization.CalendarDelete;
import com.erg.ergspringboot.calendar.authorization.CalendarPublish;
import com.erg.ergspringboot.calendar.authorization.CalendarRead;
import com.erg.ergspringboot.calendar.authorization.CalendarUpdate;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.BatchResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.calendar.CalendarHolidayPort.DayLoad;
import com.erg.ergspringboot.calendar.TeachingScheduleReuseInput;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LoadSettingsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.OverviewGridResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.PeriodTemplateResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ReusePreviewResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPeriodAssignmentResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotOptionsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolTravelTimeResponse;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Teaching schedule v2 operations (KE_HOACH_LICH_DAY.md 4.2).
 */
@Controller
public class TeachingScheduleGraphQlController {

    private static final String[] MANAGE = {"lms.schedule.manage", "lms.manage", "*"};

    private final TeachingScheduleOperations schedule;
    private final GraphQlAccessSupport accessSupport;
    private final CalendarScopeService scopeService;

    public TeachingScheduleGraphQlController(TeachingScheduleOperations schedule, GraphQlAccessSupport accessSupport,
            CalendarScopeService scopeService) {
        this.schedule = schedule;
        this.accessSupport = accessSupport;
        this.scopeService = scopeService;
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingPeriodTemplates")
    @CalendarRead
    public List<PeriodTemplateResponse> teachingPeriodTemplates() {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        return schedule.periodTemplates(accessSupport.verifiedTenantId(principal, null));
    }

    /** Dated period tables per school; schoolIds null = every school the caller manages. */
    @SchemaMapping(typeName = "LcmsQuery", field = "schoolPeriodAssignments")
    @CalendarRead
    public List<SchoolPeriodAssignmentResponse> schoolPeriodAssignments(@Argument List<String> schoolIds) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        List<String> scope = schoolIds == null || schoolIds.isEmpty() ? visibleSchools(principal)
                : scopeService.requireManagedSchools(principal, schoolIds);
        return schedule.schoolPeriodAssignments(accessSupport.verifiedTenantId(principal, null), scope);
    }

    /**
     * Creates a template, or replaces one no batch uses yet. Replacing a template other schools share needs scope over
     * all of them, and the tenant default needs scope over every school.
     */
    @MutationMapping
    public PeriodTemplateResponse saveTeachingPeriodTemplate(@Argument TeachingPeriodTemplateInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, MANAGE);
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        if (input.id() != null) {
            if (schedule.isDefaultTemplate(tenantId, input.id()) && visibleSchools(principal) != null) {
                throw new ForbiddenException("Bảng giờ mặc định chỉ người quản lý mọi trường được sửa.");
            }
            scopeService.requireManagedSchools(principal, schedule.templateSchools(tenantId, input.id()));
        }
        return schedule.saveTemplate(tenantId, input);
    }

    @MutationMapping
    public SchoolPeriodAssignmentResponse assignSchoolPeriodTemplate(@Argument String schoolId, @Argument String templateId,
            @Argument String effectiveFrom) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, MANAGE);
        scopeService.requireManagedSchools(principal, List.of(schoolId));
        return schedule.assignTemplate(accessSupport.verifiedTenantId(principal, null), schoolId, templateId, effectiveFrom,
                accessSupport.blankToNull(principal.userId()));
    }

    /** New template + assignment in one transaction: the Luu gio tiet button of the board. */
    @MutationMapping
    public SchoolPeriodAssignmentResponse saveSchoolPeriodTimes(@Argument SchoolPeriodTimesInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, MANAGE);
        scopeService.requireManagedSchools(principal, List.of(input.schoolId()));
        return schedule.saveSchoolPeriodTimes(accessSupport.verifiedTenantId(principal, null), input,
                accessSupport.blankToNull(principal.userId()));
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "schoolTravelTimes")
    @CalendarRead
    public List<SchoolTravelTimeResponse> schoolTravelTimes(
            @Argument String search, @Argument int page, @Argument int size) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        return schedule.travelTimes(accessSupport.verifiedTenantId(principal, null), search, page, size);
    }

    @MutationMapping
    public SchoolTravelTimeResponse upsertSchoolTravelTime(
            @Argument String schoolAId, @Argument String schoolBId, @Argument int minutes) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, MANAGE);
        scopeService.requireManagedSchools(principal, List.of(schoolAId, schoolBId));
        return schedule.upsertTravelTime(accessSupport.verifiedTenantId(principal, null), schoolAId, schoolBId, minutes,
                accessSupport.blankToNull(principal.userId()));
    }

    @MutationMapping
    public boolean deleteSchoolTravelTime(@Argument String schoolAId, @Argument String schoolBId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, MANAGE);
        scopeService.requireManagedSchools(principal, List.of(schoolAId, schoolBId));
        return schedule.deleteTravelTime(accessSupport.verifiedTenantId(principal, null), schoolAId, schoolBId);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleDraft")
    @CalendarRead
    public DraftResultResponse teachingScheduleDraft(@Argument String batchId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return schedule.draft(tenantId, batchId);
    }

    @MutationMapping
    @CalendarCreate
    public DraftResultResponse saveTeachingScheduleDraft(@Argument TeachingScheduleDraftInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.create", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireManagedSchools(principal, List.of(input.schoolId()));
        if (input.batchId() != null) scopeService.requireBatch(principal, tenantId, input.batchId());
        return schedule.saveDraft(tenantId, input, accessSupport.blankToNull(principal.userId()));
    }

    @MutationMapping
    @CalendarPublish
    public BatchResponse publishTeachingScheduleBatch(@Argument String batchId, @Argument int expectedVersion) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.publish", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return schedule.publishBatch(tenantId, batchId, expectedVersion, accessSupport.blankToNull(principal.userId()));
    }

    @MutationMapping
    @CalendarDelete
    public BatchResponse cancelTeachingScheduleBatch(@Argument String batchId, @Argument int expectedVersion) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.delete", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return schedule.cancelBatch(tenantId, batchId, expectedVersion, accessSupport.blankToNull(principal.userId()));
    }

    @MutationMapping
    @CalendarUpdate
    public BatchResponse renameTeachingScheduleBatch(@Argument String batchId, @Argument String label,
            @Argument int expectedVersion) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.update", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return schedule.renameBatch(tenantId, batchId, label, expectedVersion);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleSlotOptions")
    @CalendarRead
    public SlotOptionsResponse teachingScheduleSlotOptions(@Argument String schoolId, @Argument String batchId,
            @Argument String date, @Argument int periodNo, @Argument String classId, @Argument String excludeEventId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireManagedSchools(principal, List.of(schoolId));
        if (batchId != null) scopeService.requireBatch(principal, tenantId, batchId);
        return schedule.slotOptions(tenantId, schoolId, batchId, parseDate(date), periodNo, classId, excludeEventId);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "previewTeachingScheduleReuse")
    @CalendarRead
    public ReusePreviewResponse previewTeachingScheduleReuse(@Argument TeachingScheduleReuseInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        scopeService.requireManagedSchools(principal, List.of(input.sourceSchoolId()));
        return schedule.previewReuse(accessSupport.verifiedTenantId(principal, null), input);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleOverviewSchools")
    @CalendarRead
    public SchoolPageResponse teachingScheduleOverviewSchools(@Argument String weekStart, @Argument String search,
            @Argument String quick, @Argument int page, @Argument int size) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        return schedule.overviewSchools(accessSupport.verifiedTenantId(principal, null), visibleSchools(principal),
                parseDate(weekStart), search, quick, page, size);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleOverviewGrid")
    @CalendarRead
    public OverviewGridResponse teachingScheduleOverviewGrid(@Argument String weekStart, @Argument List<String> schoolIds) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        List<String> allowed = scopeService.requireManagedSchools(principal, schoolIds);
        return schedule.overviewGrid(accessSupport.verifiedTenantId(principal, null), parseDate(weekStart), allowed);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleOverviewTeachers")
    @CalendarRead
    public TeacherPageResponse teachingScheduleOverviewTeachers(@Argument String weekStart, @Argument String search,
            @Argument String quick, @Argument String sort, @Argument int page, @Argument int size) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        return schedule.overviewTeachers(accessSupport.verifiedTenantId(principal, null), visibleSchools(principal),
                parseDate(weekStart), search, quick, sort, page, size);
    }

    /** Longest range the days-off month view may ask for (six weeks). */
    static final int MAX_DAY_LOAD_DAYS = 42;

    /**
     * Published periods per day. HR (timekeeping.manage) sees every school because a day off is system-wide; a
     * schedule reader sees the schools in their managed scope.
     */
    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleDayLoads")
    @CalendarDayLoadRead
    public List<DayLoad> teachingScheduleDayLoads(@Argument String from, @Argument String to) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        LocalDate start = parseDate(from);
        LocalDate end = parseDate(to);
        if (end.isBefore(start) || start.plusDays(MAX_DAY_LOAD_DAYS).isBefore(end)) {
            throw TeachingScheduleException.badRequest("INVALID_DATE_RANGE", "Khoảng ngày tối đa " + MAX_DAY_LOAD_DAYS + " ngày.");
        }
        List<String> scope = accessSupport.isAllowed(principal, "timekeeping.manage") ? null : visibleSchools(principal);
        return schedule.dayLoads(accessSupport.verifiedTenantId(principal, null), start, end, scope);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleLoadSettings")
    @CalendarRead
    public LoadSettingsResponse teachingScheduleLoadSettings() {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        return schedule.loadSettings(accessSupport.verifiedTenantId(principal, null));
    }

    @MutationMapping
    @CalendarCreate
    public DraftResultResponse reuseTeachingScheduleWeek(@Argument TeachingScheduleReuseInput input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.create", "lms.schedule.manage", "lms.manage", "*");
        scopeService.requireManagedSchools(principal, List.of(input.sourceSchoolId()));
        return schedule.reuseWeek(accessSupport.verifiedTenantId(principal, null), input, accessSupport.blankToNull(principal.userId()));
    }

    @MutationMapping
    public LoadSettingsResponse updateTeachingScheduleLoadSettings(@Argument int loadWarn, @Argument int loadMax) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, MANAGE);
        return schedule.updateLoadSettings(accessSupport.verifiedTenantId(principal, null), loadWarn, loadMax,
                accessSupport.blankToNull(principal.userId()));
    }

    /** The schools the caller may see: null (all of the tenant) for wildcard principals, else their managed schools. */
    private List<String> visibleSchools(ErgPrincipal principal) {
        if (accessSupport.isWildcardPrincipal(principal)) return null;
        List<String> managed = scopeService.requireManagedSchools(principal, List.of());
        return managed.contains("*") ? null : managed;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException | NullPointerException e) {
            throw TeachingScheduleException.badRequest("INVALID_DATE", "Ngày không hợp lệ: " + value);
        }
    }
}
