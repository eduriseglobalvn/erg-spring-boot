package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.calendar.internal.batch.CalendarBatchLifecycleUseCase;
import com.erg.ergspringboot.calendar.internal.schedule.PeriodTemplateService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleLoadSettingsService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleOverviewService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleReuseService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleSlotOptionsService;
import com.erg.ergspringboot.calendar.internal.schedule.ScheduleTeacherOverviewService;
import com.erg.ergspringboot.calendar.internal.schedule.TeachingScheduleDraftService;
import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService;
import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService.School;
import com.erg.ergspringboot.calendar.internal.schedule.TravelTimeService.TravelTime;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.BatchResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.DraftResultResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LoadSettingsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.OverviewGridResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.PeriodTemplateResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.ReusePreviewResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolPageResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolRowResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SchoolTravelTimeResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.SlotOptionsResponse;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.TeacherPageResponse;
import com.erg.ergspringboot.calendar.CalendarHolidayPort.DayLoad;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * Public facade of the teaching schedule v2 use cases; controllers talk to this, never to calendar.internal.
 * Overview methods take {@code scope}: the school ids the caller may see, or null for every school of the tenant.
 */
@Service
public class TeachingScheduleOperations {

    private final PeriodTemplateService templates;
    private final TravelTimeService travelTimes;
    private final TeachingScheduleDraftService drafts;
    private final CalendarBatchLifecycleUseCase lifecycle;
    private final ScheduleSlotOptionsService slotOptions;
    private final ScheduleReuseService reuse;
    private final ScheduleOverviewService overview;
    private final ScheduleTeacherOverviewService teacherOverview;
    private final ScheduleLoadSettingsService loadSettings;
    private final CalendarHolidayPort holidays;

    public TeachingScheduleOperations(PeriodTemplateService templates, TravelTimeService travelTimes,
            TeachingScheduleDraftService drafts, CalendarBatchLifecycleUseCase lifecycle, ScheduleSlotOptionsService slotOptions,
            ScheduleReuseService reuse, ScheduleOverviewService overview, ScheduleTeacherOverviewService teacherOverview,
            ScheduleLoadSettingsService loadSettings,
            CalendarHolidayPort holidays) {
        this.templates = templates;
        this.travelTimes = travelTimes;
        this.drafts = drafts;
        this.lifecycle = lifecycle;
        this.slotOptions = slotOptions;
        this.reuse = reuse;
        this.overview = overview;
        this.teacherOverview = teacherOverview;
        this.loadSettings = loadSettings;
        this.holidays = holidays;
    }

    public DraftResultResponse draft(String tenantId, String batchId) {
        return drafts.draft(tenantId, batchId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public DraftResultResponse saveDraft(String tenantId, TeachingScheduleDraftInput input, String actorId) {
        return drafts.saveDraft(tenantId, input, actorId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public BatchResponse publishBatch(String tenantId, String batchId, int expectedVersion, String actorId) {
        return lifecycle.publishBatch(tenantId, batchId, expectedVersion, actorId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public BatchResponse cancelBatch(String tenantId, String batchId, int expectedVersion, String actorId) {
        return lifecycle.cancelBatch(tenantId, batchId, expectedVersion, actorId);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public BatchResponse renameBatch(String tenantId, String batchId, String label, int expectedVersion) {
        return lifecycle.renameBatch(tenantId, batchId, label, expectedVersion);
    }

    public List<PeriodTemplateResponse> periodTemplates(String tenantId) {
        return templates.list(tenantId).stream().map(ScheduleOverviewService::toResponse).toList();
    }

    public SlotOptionsResponse slotOptions(String tenantId, String schoolId, String batchId, LocalDate date, int periodNo,
            String classId, String excludeEventId) {
        return slotOptions.options(tenantId, schoolId, batchId, date, periodNo, classId, excludeEventId);
    }

    public ReusePreviewResponse previewReuse(String tenantId, TeachingScheduleReuseInput input) {
        return reuse.preview(tenantId, input);
    }

    @CacheEvict(cacheNames = "calendarWorkspace", allEntries = true)
    public DraftResultResponse reuseWeek(String tenantId, TeachingScheduleReuseInput input, String actorId) {
        return reuse.reuse(tenantId, input, actorId);
    }

    public SchoolPageResponse overviewSchools(String tenantId, Collection<String> scope, LocalDate weekStart, String search,
            String quick, int page, int size) {
        return overview.schools(tenantId, scope, weekStart, search, quick, page, size);
    }

    public OverviewGridResponse overviewGrid(String tenantId, LocalDate weekStart, List<String> schoolIds) {
        return overview.grid(tenantId, weekStart, schoolIds);
    }

    public TeacherPageResponse overviewTeachers(String tenantId, Collection<String> scope, LocalDate weekStart, String search,
            String quick, String sort, int page, int size) {
        return teacherOverview.teachers(tenantId, scope, weekStart, search, quick, sort, page, size);
    }

    /** Published periods and schools per day in [from, to]; {@code scope} null = every school of the tenant. */
    public List<DayLoad> dayLoads(String tenantId, LocalDate from, LocalDate to, Collection<String> scope) {
        return holidays.dayLoads(tenantId, from, to, scope);
    }

    public LoadSettingsResponse loadSettings(String tenantId) {
        return loadSettings.get(tenantId);
    }

    public LoadSettingsResponse updateLoadSettings(String tenantId, int loadWarn, int loadMax, String actorId) {
        return loadSettings.update(tenantId, loadWarn, loadMax, actorId);
    }

    public List<SchoolTravelTimeResponse> travelTimes(String tenantId, String search, int page, int size) {
        return travelTimes.list(tenantId, search, page, size).stream().map(TeachingScheduleOperations::toResponse).toList();
    }

    public SchoolTravelTimeResponse upsertTravelTime(String tenantId, String schoolA, String schoolB, int minutes,
            String actorId) {
        return toResponse(travelTimes.upsert(tenantId, schoolA, schoolB, minutes, actorId));
    }

    public boolean deleteTravelTime(String tenantId, String schoolA, String schoolB) {
        return travelTimes.delete(tenantId, schoolA, schoolB);
    }

    private static SchoolTravelTimeResponse toResponse(TravelTime travel) {
        return new SchoolTravelTimeResponse(row(travel.schoolA()), row(travel.schoolB()), travel.minutes(),
                travel.updatedAt() == null ? null : travel.updatedAt().toString());
    }

    // Overview counters belong to the weekly overview query; a travel-time row has none.
    private static SchoolRowResponse row(School school) {
        return new SchoolRowResponse(school.id(), school.name(), school.shortName(), 0, false, 0);
    }
}
