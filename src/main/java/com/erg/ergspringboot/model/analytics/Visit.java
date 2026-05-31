package com.erg.ergspringboot.model.analytics;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class Visit extends BaseEntity {

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
