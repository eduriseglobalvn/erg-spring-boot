package com.erg.ergspringboot.model.notifications;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/notifications/domain/entity/notification.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryLog {

    private String id;

    private String notificationId;

    private String provider;

    private Integer attempt;

    private String status;

    private Integer hTTPStatus;

    private String responseBody;

    private String errorMsg;

    private Long durationMS;

    private OffsetDateTime createdAt;
}
