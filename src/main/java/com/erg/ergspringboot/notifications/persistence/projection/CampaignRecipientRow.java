package com.erg.ergspringboot.notifications.persistence.projection;

import java.time.OffsetDateTime;

public record CampaignRecipientRow(String userId, OffsetDateTime readAt, OffsetDateTime ackedAt) {
}
