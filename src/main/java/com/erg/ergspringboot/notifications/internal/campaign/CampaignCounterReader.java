package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.notifications.persistence.NotificationMessageRepository;
import com.erg.ergspringboot.notifications.persistence.projection.CampaignDeliveryCounts;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-page delivery counters of the campaign list, cached 30 s per tenant and exact page (the key is the ordered id
 * digest). Opens and acknowledgements reach the counters after the TTL at most; send/create change the page's ids.
 */
@Component
public class CampaignCounterReader {

    public record Counters(List<CampaignDeliveryCounts> rows) {}

    private final NotificationMessageRepository messages;

    public CampaignCounterReader(NotificationMessageRepository messages) {
        this.messages = messages;
    }

    @Cacheable(cacheNames = "notificationCampaignCounts", sync = true, key = "{#tenantId, #pageKey}")
    @Transactional(readOnly = true)
    public Counters load(String tenantId, String pageKey, List<String> campaignIds) {
        return new Counters(messages.countByCampaigns(tenantId, campaignIds));
    }
}
