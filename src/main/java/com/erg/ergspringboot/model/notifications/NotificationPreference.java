package com.erg.ergspringboot.model.notifications;

import com.erg.ergspringboot.model.BaseEntity;
import java.util.List;
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
public class NotificationPreference extends BaseEntity {

    private String userId;

    private String email;

    private String discord;

    private String telegram;

    private String whatsApp;

    private String slack;

    private List<WebhookPref> webhooks;

    private String digestFreq;

    private String digestTime;

    private String language;
}
