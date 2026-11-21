package com.erg.ergspringboot.controller.calendar.graphql;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CreateRoomRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.DeleteTeacherScheduleRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBulkPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarDeletePayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPreviewPayload;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRoomOption;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.calendar.CalendarOperations;
import com.erg.ergspringboot.calendar.CalendarCommandPort;
import com.erg.ergspringboot.calendar.authorization.CalendarCreate;
import com.erg.ergspringboot.calendar.authorization.CalendarDelete;
import com.erg.ergspringboot.calendar.authorization.CalendarUpdate;
import com.erg.ergspringboot.calendar.CalendarScopeService;
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
    public CalendarMutationGraphQlController(CalendarCommandPort commands,
            GraphQlAccessSupport accessSupport, CalendarScopeService scopeService) {
        this(new CalendarOperations(commands), accessSupport, scopeService);
    }

    @MutationMapping
    @CalendarCreate
    public CalendarPreviewPayload previewTeachingSchedule(@Argument CalendarBulkCreateRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.create", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());
        return calendar.preview(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    @CalendarCreate
    public CalendarBulkPayload createTeachingSchedule(@Argument CalendarBulkCreateRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.create", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());
        return calendar.create(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    @CalendarUpdate
    public CalendarEventPayload updateTeachingScheduleEvent(@Argument CalendarEventUpdateRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.update", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        scopeService.requireEvent(principal, tenantId, input.eventId());
        return calendar.update(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    @CalendarDelete
    public CalendarDeletePayload deleteTeachingScheduleEvent(@Argument CalendarEventDeleteRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.delete", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        scopeService.requireEvent(principal, tenantId, input.eventId());
        return calendar.delete(tenantId, input, resolveUserId(principal));
    }

    @MutationMapping
    @CalendarDelete
    public CalendarDeletePayload deleteTeacherSchedule(@Argument DeleteTeacherScheduleRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.delete", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        com.erg.ergspringboot.calendar.internal.mutation.CalendarEventMutationUseCase.requireSchoolIds(input.schoolIds());
        List<String> schoolIds = scopeService.requireManagedSchools(principal, input.schoolIds());
        return calendar.deleteTeacherSchedule(
                tenantId, input.teacherUserId(), schoolIds,
                input.dateFrom(), input.dateTo(), resolveUserId(principal));
    }

    @MutationMapping
    @CalendarCreate
    public CalendarRoomOption createRoom(@Argument CreateRoomRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.create", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());
        return calendar.createRoom(tenantId, input.schoolId(), input.roomName(), resolveUserId(principal));
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
