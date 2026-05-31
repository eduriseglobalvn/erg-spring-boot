package com.erg.ergspringboot.model.analytics;

import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/analytics/domain/entity/firebase_sync.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseSyncEvent {

    private String id;

    private String tenantId;

    private String eventName;

    private String platform;

    private String userId;

    private String deviceId;

    private String sessionId;

    private Map<String, Object> params;

    private String country;

    private String city;

    private String appInstanceId;

    private OffsetDateTime receivedAt;

    private OffsetDateTime processedAt;

    private OffsetDateTime createdAt;
}
