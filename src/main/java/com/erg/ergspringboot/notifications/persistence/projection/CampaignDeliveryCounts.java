package com.erg.ergspringboot.notifications.persistence.projection;

/** Per-campaign recipients, how many opened (read) and how many acknowledged. */
public record CampaignDeliveryCounts(String campaignId, Long recipients, Long viewed, Long acked) {
}
