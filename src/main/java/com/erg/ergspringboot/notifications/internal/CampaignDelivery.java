package com.erg.ergspringboot.notifications.internal;

import com.erg.ergspringboot.notifications.NotificationPriority;
import java.util.List;

/** One campaign send: every recipient gets their own inbox message so read/ack is tracked per person. */
public record CampaignDelivery(
        String tenantId,
        String createdBy,
        String campaignId,
        String title,
        String body,
        NotificationPriority priority,
        boolean pushEnabled,
        List<String> userIds) {
}
