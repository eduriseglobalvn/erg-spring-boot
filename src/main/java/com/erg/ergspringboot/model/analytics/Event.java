package com.erg.ergspringboot.model.analytics;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/analytics/domain/entity/event.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    private String id;

    private String tenantId;

    private String sessionId;

    private Long userId;

    private String eventName;

    private String eventType;

    private Map<String, Object> properties;

    private OffsetDateTime createdAt;
}
