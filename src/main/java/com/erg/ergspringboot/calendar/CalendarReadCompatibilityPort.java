package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarEventResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarFilterOptionsResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarLaneSummaryResponse;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarPermissionsResponse;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.model.calendar.CalendarEventTeacher;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Compatibility-only projection boundary for the old
 * {@code CalendarReadService}. New callers must use {@link CalendarReadPort},
 * which exposes only module-owned response contracts.
 */
@Deprecated(since = "2026-08-08")
public interface CalendarReadCompatibilityPort {

    CalendarEventResponse buildEventResponse(
            CalendarEvent event, List<CalendarEventTeacher> teachers, CalendarReferenceMaps references);

    CalendarFilterOptionsResponse buildFilterOptions(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            CalendarReferenceMaps references,
            String viewMode);

    List<CalendarLaneSummaryResponse> buildLaneSummaries(
            List<CalendarEvent> events,
            Map<String, List<CalendarEventTeacher>> assignmentsByEvent,
            CalendarReferenceMaps references,
            String viewMode);

    CalendarCatalogResponse buildCatalog(
            String tenantId, List<String> schoolIds, OffsetDateTime from, OffsetDateTime to);

    CalendarPermissionsResponse buildPermissions(ErgPrincipal principal);
}
