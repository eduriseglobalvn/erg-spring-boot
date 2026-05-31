package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/notifications/domain/entity/notification.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Notification extends BaseEntity {

    private String userId;

    private String userIdText;

    private String type;

    private String channel;

    private String recipient;

    private String title;

    private String subject;

    private String message;

    private String body;

    private String hTMLBody;

    private String template;

    private Map<String, String> data;

    private Map<String, Object> metadata;

    private String status;

    private String priority;

    private Integer retryCount;

    private Integer maxRetries;

    private String provider;

    private String errorMsg;

    private Boolean read;

    private Boolean digested;

    private String digestId;

    private OffsetDateTime readAt;

    private OffsetDateTime legacyReadAt;

    private String actionUrl;

    private String groupKey;

    private String source;

    private OffsetDateTime scheduledAt;

    private OffsetDateTime sentAt;

    private OffsetDateTime deliveredAt;

    private OffsetDateTime expiresAt;
}
