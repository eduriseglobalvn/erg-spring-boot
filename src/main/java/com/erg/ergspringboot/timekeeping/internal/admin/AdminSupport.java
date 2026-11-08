package com.erg.ergspringboot.timekeeping.internal.admin;

import com.erg.ergspringboot.timekeeping.TimekeepingException;
import com.erg.ergspringboot.timekeeping.internal.schedule.WorkContextResolver;
import com.erg.ergspringboot.timekeeping.persistence.TkAuditLog;
import com.erg.ergspringboot.timekeeping.persistence.TkAuditLogRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.erg.ergspringboot.users.persistence.organization.EducationUnitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Shared admin helpers: the school belongs to the caller's tenant, audit rows, names, today in Vietnam. */
@Component
class AdminSupport {

    static final String SELF = "timekeeping.self";
    static final String MANAGE = "timekeeping.manage";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final EducationUnitRepository units;
    private final UserRepository users;
    private final TkAuditLogRepository audit;
    private final Clock clock;

    AdminSupport(EducationUnitRepository units, UserRepository users, TkAuditLogRepository audit, Clock clock) {
        this.units = units;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** 404 unless {@code schoolId} is an education unit of the tenant (the client never names the tenant). */
    EducationUnit requireSchool(String tenantId, String schoolId) {
        if (schoolId == null || schoolId.isBlank()) throw bad("SCHOOL_REQUIRED", "Chọn cơ sở");
        return units.findByIdAndTenantId(schoolId, tenantId)
                .orElseThrow(() -> notFound("Không tìm thấy cơ sở"));
    }

    Map<String, String> schoolNames(String tenantId, Collection<String> schoolIds) {
        return units.findByTenantIdOrderByNameAsc(tenantId).stream().filter(u -> schoolIds.contains(u.getId()))
                .collect(Collectors.toMap(EducationUnit::getId, EducationUnit::getName, (a, b) -> a));
    }

    Map<String, String> names(String tenantId, Collection<String> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return users.findByTenantIdAndIdInAnyStatus(tenantId, userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() == null ? "" : u.getFullName(), (a, b) -> a));
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(WorkContextResolver.ZONE));
    }

    Clock clock() {
        return clock;
    }

    void audit(String tenantId, String actorId, String action, String targetType, String targetId,
            Map<String, Object> detail) {
        TkAuditLog row = new TkAuditLog();
        row.setTenantId(tenantId);
        row.setActorId(actorId);
        row.setAction(action);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setDetail(new LinkedHashMap<>(detail));
        row.setAt(clock.instant());
        audit.save(row);
    }

    /** Audit row for a configuration write: {"before": ..., "after": ...} (null when created / deleted). */
    void auditChange(String tenantId, String actorId, String action, String type, String id,
            Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        audit(tenantId, actorId, action, type, id, detail);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> summary(Object view) {
        return JSON.convertValue(view, LinkedHashMap.class);
    }

    static String text(String value, int max, String code, String message) {
        if (value == null || value.isBlank()) throw bad(code, message);
        if (value.strip().length() > max) throw bad("TEXT_TOO_LONG", "Tối đa " + max + " ký tự");
        return value.strip();
    }

    static YearMonth period(String period) {
        try {
            if (period == null || !period.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("", "", 0);
            return YearMonth.parse(period);
        } catch (DateTimeParseException invalid) {
            throw bad("INVALID_PERIOD", "Kỳ phải có dạng yyyy-MM");
        }
    }

    static TimekeepingException bad(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.BAD_REQUEST, code, messageVi);
    }

    static TimekeepingException conflict(String code, String messageVi) {
        return new TimekeepingException(HttpStatus.CONFLICT, code, messageVi);
    }

    static TimekeepingException notFound(String messageVi) {
        return new TimekeepingException(HttpStatus.NOT_FOUND, "NOT_FOUND", messageVi);
    }
}
