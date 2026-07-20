package com.erg.ergspringboot.controller.calendar.graphql;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarExcelImportRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CreateRoomRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.DeleteTeacherScheduleRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.calendar.CalendarOperations;
import com.erg.ergspringboot.service.calendar.CalendarCommandService;
import com.erg.ergspringboot.service.calendar.CalendarScopeService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
public class CalendarMutationGraphQlController {

    private final CalendarOperations calendar;
    private final GraphQlAccessSupport accessSupport;
    private final CalendarScopeService scopeService;

    @Autowired
    public CalendarMutationGraphQlController(
            CalendarOperations calendar,
            GraphQlAccessSupport accessSupport,
            CalendarScopeService scopeService
    ) {
        this.calendar = calendar;
        this.accessSupport = accessSupport;
        this.scopeService = scopeService;
    }

    /** Compatibility constructor retained for existing focused adapter tests. */
    @Deprecated
    public CalendarMutationGraphQlController(CalendarCommandService commands,
            GraphQlAccessSupport accessSupport, CalendarScopeService scopeService) {
        this(new CalendarOperations(commands), accessSupport, scopeService);
    }

    @MutationMapping
    public CalendarPreviewPayload previewTeachingSchedule(@Argument CalendarBulkCreateRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());
        return calendar.preview(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    public CalendarBulkPayload createTeachingSchedule(@Argument CalendarBulkCreateRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());
        return calendar.create(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    public CalendarEventPayload updateTeachingScheduleEvent(@Argument CalendarEventUpdateRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        scopeService.requireEvent(principal, tenantId, input.eventId());
        return calendar.update(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    public CalendarDeletePayload deleteTeachingScheduleEvent(@Argument CalendarEventDeleteRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        scopeService.requireEvent(principal, tenantId, input.eventId());
        return calendar.delete(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    public CalendarDeletePayload deleteTeacherSchedule(@Argument DeleteTeacherScheduleRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        List<String> schoolIds = scopeService.requireManagedSchools(principal, input.schoolIds());
        return calendar.deleteTeacherSchedule(
                tenantId, input.teacherUserId(), schoolIds,
                input.dateFrom(), input.dateTo(), resolveUserId(principal));
    }

    @MutationMapping
    public CalendarBatchPayload publishTeachingScheduleBatch(@Argument String batchId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return calendar.publish(tenantId, batchId, resolveUserId(principal));
    }

    @MutationMapping
    public CalendarBatchPayload cancelTeachingScheduleBatch(@Argument String batchId) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return calendar.cancel(tenantId, batchId, resolveUserId(principal));
    }

    @MutationMapping
    public CalendarRoomOption createRoom(@Argument CreateRoomRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());
        return calendar.createRoom(tenantId, input.schoolId(), input.roomName(), resolveUserId(principal));
    }

    /**
     * STUB: Imports a teaching schedule from an Excel file via GraphQL.
     * <p>
     * Note: GraphQL cannot handle binary file uploads directly. This mutation
     * accepts only metadata fields. The actual file upload should use the
     * REST endpoint {@code POST /api/v1/calendar/import-excel} which accepts
     * a MultipartFile.
     * </p>
     *
     * @param input metadata about the import (tenant, school, dates, etc.)
     * @return a preview payload with the parsed results
     * @throws UnsupportedOperationException always, until Excel import is implemented
     */
    @MutationMapping
    public CalendarPreviewPayload importTeachingScheduleFromExcel(
            @Argument CalendarExcelImportRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());

        // Future: parse the Excel file, then delegate to commandService.preview()
        // For now, throw clearly.
        throw new UnsupportedOperationException(
                "Excel import not yet implemented. Use createTeachingSchedule with bulk input instead.");
    }

    private String resolveUserId(ErgPrincipal principal) {
        return accessSupport.blankToNull(principal == null ? null : principal.userId());
    }

    private void requireManagedSchool(ErgPrincipal principal, String schoolId) {
        if (schoolId == null || schoolId.isBlank()) {
            throw new BadRequestException("schoolId is required.");
        }
        scopeService.requireManagedSchools(principal, List.of(schoolId));
    }
}
