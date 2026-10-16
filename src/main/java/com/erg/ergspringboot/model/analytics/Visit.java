package com.erg.ergspringboot.model.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/analytics/domain/entity/visit.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class Visit {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private Long userId;

    private String sessionId;

    private String pageUrl;

    private String referrer;

    private String ipHash;

    private String userAgent;

    private String deviceType;

    private String browser;

    private String oS;

    private String country;

    private String city;

    private String region;

    private String timezone;

    private String entityType;

    private String entityId;

    private Integer durationSeconds;

    private Integer pageViews;
}
