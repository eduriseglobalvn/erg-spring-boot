package com.erg.ergspringboot.controller.calendar.graphql;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarWorkspaceRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.TeachingScheduleBatchDetailRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.TeachingScheduleBatchListRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchDetailResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarBatchPageResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarWorkspaceResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.calendar.CalendarOperations;
import com.erg.ergspringboot.service.calendar.CalendarReadService;
import com.erg.ergspringboot.service.calendar.CalendarScopeService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class CalendarLcmsQueryGraphQlController {

    private final CalendarOperations calendar;
    private final GraphQlAccessSupport accessSupport;
    private final CalendarScopeService scopeService;

    @org.springframework.beans.factory.annotation.Autowired
    public CalendarLcmsQueryGraphQlController(
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
    public CalendarLcmsQueryGraphQlController(CalendarReadService reads,
            GraphQlAccessSupport accessSupport, CalendarScopeService scopeService) {
        this(new CalendarOperations(reads), accessSupport, scopeService);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingCalendarWorkspace")
    public CalendarWorkspaceResponse teachingCalendarWorkspace(@Argument CalendarWorkspaceRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input == null ? null : input.tenantId());

        List<String> schoolIds = scopeService.requireManagedSchools(principal, input == null ? null : input.schoolIds());

        CalendarWorkspaceRequest safeInput = input == null ? CalendarWorkspaceRequest.empty() : input;
        return calendar.workspace(
                tenantId, schoolIds, safeInput.from(), safeInput.to(), safeInput.viewMode(),
                safeInput.teacherIds(), safeInput.classIds(), safeInput.subjectIds(), safeInput.levelIds(),
                safeInput.statuses(), principal);
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleBatches")
    public CalendarBatchPageResponse teachingScheduleBatches(@Argument TeachingScheduleBatchListRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input == null ? null : input.tenantId());
        TeachingScheduleBatchListRequest safeInput = input == null
                ? new TeachingScheduleBatchListRequest(null, null, null, null, null)
                : input;
        String schoolId = resolveScopedSchoolFilter(principal, safeInput.schoolId());
        return calendar.batches(
                tenantId, schoolId, safeInput.status(), safeInput.page(), safeInput.size());
    }

    @SchemaMapping(typeName = "LcmsQuery", field = "teachingScheduleBatch")
    public CalendarBatchDetailResponse teachingScheduleBatch(@Argument TeachingScheduleBatchDetailRequest input) {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        accessSupport.assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = accessSupport.verifiedTenantId(principal, input == null ? null : input.tenantId());
        if (input == null || input.batchId() == null || input.batchId().isBlank()) {
            throw new IllegalArgumentException("batchId is required.");
        }
        scopeService.requireBatch(principal, tenantId, input.batchId());
        return calendar.batch(tenantId, input.batchId());
    }

    private String resolveScopedSchoolFilter(ErgPrincipal principal, String requestedSchoolId) {
        if (requestedSchoolId != null && !requestedSchoolId.isBlank()) {
            String schoolId = requestedSchoolId.trim();
            scopeService.requireManagedSchools(principal, List.of(schoolId));
            return schoolId;
        }
        if (accessSupport.isWildcardPrincipal(principal)) {
            return null;
        }
        List<String> managed = scopeService.requireManagedSchools(principal, List.of());
        if (managed.size() == 1) {
            return managed.getFirst();
        }
        throw new BadRequestException("schoolId is required when more than one school is managed.");
    }
}
