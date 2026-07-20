package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.model.calendar.CalendarEvent;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Boundary duy nhất để resolve và assert tenant/managed-school scope cho mọi calendar operation.
 * <p>
 * Đảm bảo LCMS không chấp nhận {@code schoolIds} client gửi mà không kiểm tra managed scope,
 * và update/delete/publish/cancel kiểm tra school của resource thật.
 */
@Service
public class CalendarScopeService {

    private final GraphQlAccessSupport accessSupport;
    private final CalendarEventRepository eventRepository;
    private final CalendarBatchRepository batchRepository;

    public CalendarScopeService(
            GraphQlAccessSupport accessSupport,
            CalendarEventRepository eventRepository,
            CalendarBatchRepository batchRepository) {
        this.accessSupport = accessSupport;
        this.eventRepository = eventRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * Resolve danh sách schoolId hợp lệ từ request, đảm bảo tất cả đều trong managed scope.
     * <ul>
     *   <li>Wildcard principal: trả về normalized request.</li>
     *   <li>Request rỗng: trả về managed list.</li>
     *   <li>Request không rỗng: đảm bảo mọi id đều nằm trong managed list.</li>
     * </ul>
     */
    public List<String> requireManagedSchools(ErgPrincipal principal, List<String> requested) {
        if (accessSupport.isWildcardPrincipal(principal)) {
            return normalize(requested);
        }
        List<String> managed = accessSupport.csv(
                accessSupport.attribute(principal, "managedEducationUnitIds"));
        List<String> effective = (requested == null || requested.isEmpty())
                ? managed
                : normalize(requested);
        if (effective.isEmpty() || !managed.containsAll(effective)) {
            throw new ForbiddenException("Calendar school scope is not allowed.");
        }
        return effective;
    }

    /**
     * Assert rằng principal có quyền truy cập event theo tenant + school scope.
     * Trả về event nếu hợp lệ, throw nếu không.
     */
    public CalendarEvent requireEvent(ErgPrincipal principal, String tenantId, String eventId) {
        CalendarEvent event = eventRepository.findByTenantIdAndId(tenantId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        accessSupport.assertEducationUnitScope(principal, event.getSchoolId());
        return event;
    }

    /**
     * Assert rằng principal có quyền truy cập batch theo tenant + school scope.
     * Trả về batch nếu hợp lệ, throw nếu không.
     */
    public CalendarBatch requireBatch(ErgPrincipal principal, String tenantId, String batchId) {
        CalendarBatch batch = batchRepository.findByTenantIdAndId(tenantId, batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        accessSupport.assertEducationUnitScope(principal, batch.getSchoolId());
        return batch;
    }

    private List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}