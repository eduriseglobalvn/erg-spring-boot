package com.erg.ergspringboot.model.notifications;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/notifications/domain/entity/notification.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPref {

    private String name;

    private String url;

    private Boolean enabled;
}
