package com.erg.ergspringboot.controller.calendar.rest;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.calendar.CalendarOperations;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventDeleteRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarEventUpdateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarExcelImportRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CreateRoomRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.DeleteTeacherScheduleRequest;
import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.calendar.CalendarScopeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarRestController {

    private final CalendarOperations calendar;
    private final CalendarScopeService scopeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CalendarRestController(
            CalendarOperations calendar,
            CalendarScopeService scopeService) {
        this.calendar = calendar;
        this.scopeService = scopeService;
    }

    @GetMapping("/workspace")
    public ApiResponse<CalendarWorkspaceResponse> workspace(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) String viewMode,
            @RequestParam(required = false) List<String> schoolIds,
            @RequestParam(required = false) List<String> teacherIds,
            @RequestParam(required = false) List<String> classIds,
            @RequestParam(required = false) List<String> subjectIds,
            @RequestParam(required = false) List<String> levelIds,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String effectiveTenantId = verifiedTenantId(principal, tenantId);
        List<String> effectiveSchoolIds = scopeService.requireManagedSchools(principal, schoolIds);
        return ok("Calendar workspace loaded",
                calendar.workspace(
                        effectiveTenantId, effectiveSchoolIds, from, to, viewMode,
                        teacherIds, classIds, subjectIds, levelIds, statuses, principal),
                request);
    }

    @GetMapping("/my-calendar")
    public ApiResponse<CalendarWorkspaceResponse> myCalendar(
            @RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) String viewMode,
            @RequestParam(required = false) List<String> schoolIds,
            @RequestParam(required = false) List<String> classIds,
            @RequestParam(required = false) List<String> subjectIds,
            @RequestParam(required = false) List<String> levelIds,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.read_self", "lms.schedule.read",
                "lms.schedule.manage", "lms.manage", "*");
        String effectiveTenantId = verifiedTenantId(principal, tenantId);
        return ok("My calendar loaded",
                calendar.teacherWorkspace(
                        effectiveTenantId, principal.userId(), from, to, viewMode,
                        schoolIds, classIds, subjectIds, levelIds, statuses, principal),
                request);
    }

    @GetMapping("/events/{eventId}")
    public ApiResponse<CalendarEventResponse> eventDetail(
            @PathVariable String eventId, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");

        String tenantId = verifiedTenantId(principal, null);
        scopeService.requireEvent(principal, tenantId, eventId);
        return ok("Event loaded", calendar.event(tenantId, eventId), request);
    }

    @GetMapping("/batches")
    public ApiResponse<CalendarBatchPageResponse> batches(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String effectiveTenantId = verifiedTenantId(principal, tenantId);
        String effectiveSchoolId = resolveScopedSchoolFilter(principal, schoolId);
        return ok("Batches loaded",
                calendar.batches(effectiveTenantId, effectiveSchoolId, status, page, size),
                request);
    }

    @GetMapping("/batches/{batchId}")
    public ApiResponse<CalendarBatchDetailResponse> batchDetail(
            @PathVariable String batchId,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String effectiveTenantId = verifiedTenantId(principal, tenantId);
        scopeService.requireBatch(principal, effectiveTenantId, batchId);
        return ok("Batch loaded", calendar.batch(effectiveTenantId, batchId), request);
    }

    @PostMapping("/preview")
    public ApiResponse<CalendarPreviewPayload> preview(
            @Valid @RequestBody CalendarBulkCreateRequest body, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        requireManagedSchool(principal, body.schoolId());
        return ok("Preview generated", calendar.preview(
                verifiedTenantId(principal, body.tenantId()), body, principal.userId()), request);
    }

    @PostMapping
    public ApiResponse<CalendarBulkPayload> create(
            @Valid @RequestBody CalendarBulkCreateRequest body, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        requireManagedSchool(principal, body.schoolId());
        return ok("Calendar created", calendar.create(
                verifiedTenantId(principal, body.tenantId()), body, principal.userId()), request);
    }

    @PutMapping("/events/{eventId}")
    public ApiResponse<CalendarEventPayload> updateEvent(
            @PathVariable String eventId,
            @Valid @RequestBody CalendarEventUpdateRequest body, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = verifiedTenantId(principal, body.tenantId());
        CalendarEventUpdateRequest input = new CalendarEventUpdateRequest(
                tenantId, eventId, body.roomId(), body.roomName(), body.note());
        scopeService.requireEvent(principal, tenantId, eventId);
        return ok("Event updated", calendar.update(tenantId, input, principal.userId()), request);
    }

    @DeleteMapping("/events/{eventId}")
    public ApiResponse<CalendarDeletePayload> deleteEvent(
            @PathVariable String eventId,
            @RequestParam(required = false) String tenantId,
            HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String effectiveTenantId = verifiedTenantId(principal, tenantId);
        CalendarEventDeleteRequest input = new CalendarEventDeleteRequest(effectiveTenantId, eventId);
        scopeService.requireEvent(principal, effectiveTenantId, eventId);
        return ok("Event cancelled", calendar.delete(effectiveTenantId, input, principal.userId()), request);
    }

    @PostMapping("/teachers/delete-bulk")
    public ApiResponse<CalendarDeletePayload> deleteTeacherSchedule(
            @Valid @RequestBody DeleteTeacherScheduleRequest body, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = verifiedTenantId(principal, body.tenantId());
        List<String> schoolIds = scopeService.requireManagedSchools(principal, body.schoolIds());
        return ok("Teacher schedule cancelled", calendar.deleteTeacherSchedule(
                tenantId, body.teacherUserId(), schoolIds, body.dateFrom(), body.dateTo(), principal.userId()), request);
    }

    @PostMapping("/batches/{batchId}/publish")
    public ApiResponse<CalendarBatchPayload> publishBatch(
            @PathVariable String batchId, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return ok("Batch published", calendar.publish(tenantId, batchId, principal.userId()), request);
    }

    @PostMapping("/batches/{batchId}/cancel")
    public ApiResponse<CalendarBatchPayload> cancelBatch(
            @PathVariable String batchId, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = verifiedTenantId(principal, null);
        scopeService.requireBatch(principal, tenantId, batchId);
        return ok("Batch cancelled", calendar.cancel(tenantId, batchId, principal.userId()), request);
    }

    @GetMapping("/rooms")
    public ApiResponse<List<CalendarRoomOptionResponse>> rooms(
            @RequestParam(required = false) String schoolId, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.read", "lms.schedule.manage", "lms.manage", "*");
        String tenantId = verifiedTenantId(principal, null);

        return ok("Rooms loaded", calendar.rooms(tenantId, schoolId), request);
    }

    @PostMapping("/rooms")
    public ApiResponse<CalendarRoomOption> createRoom(
            @Valid @RequestBody CreateRoomRequest body, HttpServletRequest request) {
        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        String tenantId = verifiedTenantId(principal, body.tenantId());
        requireManagedSchool(principal, body.schoolId());
        return ok("Room created", calendar.createRoom(
                tenantId, body.schoolId(), body.roomName(), principal.userId()), request);
    }

    /**
     * STUB: Imports a teaching schedule from an Excel file.
     * <p>
     * Accepts a multipart Excel file upload along with metadata parameters.
     * Future implementation will:
     * <ol>
     *   <li>Validate the file extension (.xlsx, .xls)</li>
     *   <li>Parse the Excel workbook using Apache POI</li>
     *   <li>Convert rows into {@code CalendarBulkRowRequest} entries</li>
     *   <li>Construct a {@code CalendarBulkCreateRequest} and delegate to
     *       {@link CalendarOperations#preview(String, CalendarBulkCreateRequest, String)}</li>
     * </ol>
     *
     * @param file        the uploaded Excel file (.xlsx or .xls)
     * @param schoolId    the school identifier
     * @param applyFrom   the start date (ISO format) for the schedule
     * @param repeatWeeks number of weeks to repeat
     * @param tenantId    optional tenant identifier
     * @param note        optional note for the batch
     * @param overrideConflicts whether to override conflicting events
     * @param resolveMode conflict resolution strategy
     * @param request     the HTTP servlet request
     * @return a preview payload with parsed results
     */
    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CalendarPreviewPayload> importExcel(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") String metadata,
            HttpServletRequest request) {

        ErgPrincipal principal = getRequiredPrincipal();
        assertPermission(principal, "lms.schedule.manage", "lms.manage", "*");
        CalendarExcelImportRequest input = parseImportMetadata(metadata);
        String effectiveTenantId = verifiedTenantId(principal, input.tenantId());
        requireManagedSchool(principal, input.schoolId());

        throw new UnsupportedOperationException(
                "Excel import not yet implemented. "
                        + "File received: name=" + file.getOriginalFilename()
                        + ", size=" + file.getSize() + " bytes"
                        + ", schoolId=" + input.schoolId()
                        + ", applyFrom=" + input.applyFrom()
                        + ", tenantId=" + effectiveTenantId);
    }

    private <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object rid = request.getAttribute("requestId");
        return rid == null ? request.getHeader("X-Request-ID") : rid.toString();
    }

    private ErgPrincipal getRequiredPrincipal() {
        ErgPrincipal principal = getPrincipal();
        if (principal == null) throw new ForbiddenException("Authentication is required.");
        return principal;
    }

    private ErgPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ErgPrincipal p) return p;
        return null;
    }

    private void assertPermission(ErgPrincipal principal, String... allowed) {
        if (!isAllowed(principal, allowed))
            throw new ForbiddenException("You do not have permission to access this resource.");
    }

    private boolean isAllowed(ErgPrincipal principal, String... allowedPermissions) {
        if (principal == null) return false;
        List<String> denied = principal.deniedPermissions() == null ? List.of() : principal.deniedPermissions();
        for (String p : allowedPermissions) {
            if (denied.contains("*") || denied.contains(p)) return false;
        }
        List<String> granted = principal.permissions() == null ? List.of() : principal.permissions();
        for (String p : allowedPermissions) {
            if (granted.contains("*") || granted.contains(p)) return true;
        }
        return false;
    }

    private void requireManagedSchool(ErgPrincipal principal, String schoolId) {
        if (blankToNull(schoolId) == null) {
            throw new BadRequestException("schoolId is required.");
        }
        scopeService.requireManagedSchools(principal, List.of(schoolId));
    }

    private String resolveScopedSchoolFilter(ErgPrincipal principal, String schoolId) {
        String requested = blankToNull(schoolId);
        if (requested != null) {
            requireManagedSchool(principal, requested);
            return requested;
        }
        if (principal != null && principal.permissions() != null && principal.permissions().contains("*")) {
            return null;
        }
        List<String> managed = scopeService.requireManagedSchools(principal, List.of());
        if (managed.size() == 1) {
            return managed.getFirst();
        }
        throw new BadRequestException("schoolId is required when more than one school is managed.");
    }

    private String verifiedTenantId(ErgPrincipal principal, String requested) {
        String pt = blankToNull(principal == null ? null : principal.tenantId());
        String rq = blankToNull(requested);
        if (pt != null && rq != null && !pt.equals(rq))
            throw new ForbiddenException("Tenant scope does not match the authenticated principal.");
        return pt != null ? pt : (rq == null ? "erg" : rq);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CalendarExcelImportRequest parseImportMetadata(String metadata) {
        try {
            return objectMapper.readValue(metadata, CalendarExcelImportRequest.class);
        } catch (IOException ex) {
            throw new BadRequestException("Invalid metadata JSON for calendar Excel import.");
        }
    }

}
