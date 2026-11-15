package com.erg.ergspringboot.calendar.internal.schedule;

import com.erg.ergspringboot.calendar.TeachingScheduleResponses.LoadSettingsResponse;
import com.erg.ergspringboot.exception.TeachingScheduleException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Weekly teaching-load thresholds of a tenant (warn / max periods per week); 18 and 24 until someone edits them. */
@Service
public class ScheduleLoadSettingsService {

    static final int DEFAULT_WARN = 18;
    static final int DEFAULT_MAX = 24;

    private final JdbcTemplate jdbc;

    public ScheduleLoadSettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public LoadSettingsResponse get(String tenantId) {
        List<LoadSettingsResponse> rows = jdbc.query("select load_warn, load_max, updated_at from teaching_schedule_settings where tenant_id = ?",
                (rs, i) -> new LoadSettingsResponse(rs.getInt("load_warn"), rs.getInt("load_max"),
                        rs.getObject("updated_at", OffsetDateTime.class).toString()), tenantId);
        return rows.isEmpty() ? new LoadSettingsResponse(DEFAULT_WARN, DEFAULT_MAX, null) : rows.get(0);
    }

    @Transactional("postgresTransactionManager")
    public LoadSettingsResponse update(String tenantId, int loadWarn, int loadMax, String actorId) {
        if (loadWarn < 1 || loadMax > 60 || loadWarn > loadMax) {
            throw TeachingScheduleException.badRequest("INVALID_LOAD_SETTINGS",
                    "Ngưỡng phải từ 1 đến 60 tiết và ngưỡng cảnh báo không được lớn hơn ngưỡng tối đa.");
        }
        jdbc.update("insert into teaching_schedule_settings (tenant_id, load_warn, load_max, updated_by, updated_at)"
                + " values (?, ?, ?, ?, now()) on conflict (tenant_id) do update set load_warn = excluded.load_warn,"
                + " load_max = excluded.load_max, updated_by = excluded.updated_by, updated_at = excluded.updated_at",
                tenantId, loadWarn, loadMax, actorId);
        return get(tenantId);
    }
}
