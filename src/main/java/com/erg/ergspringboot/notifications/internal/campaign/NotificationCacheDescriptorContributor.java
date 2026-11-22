package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.common.response.notifications.NotificationResponses.AdminStatsResponse;
import com.erg.ergspringboot.notifications.NotificationCampaignResponses.CampaignSummaryResponse;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache metadata of the read-heavy notification screens. All entries are short-lived KPIs or directories that
 * tolerate staleness; keys always carry every input of the result (scope, creator, page) next to the tenant.
 */
@Component
public class NotificationCacheDescriptorContributor implements CacheDescriptorContributor {

    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        return List.of(
                descriptor("notificationRecipientDirectory", mapper.constructType(RecipientDirectoryCache.Directory.class), 45, 32),
                descriptor("notificationCampaignCounts", mapper.constructType(CampaignCounterReader.Counters.class), 30, 8),
                descriptor("notificationCampaignSummary", mapper.constructType(CampaignSummaryResponse.class), 45, 4),
                descriptor("notificationOutboxStats", mapper.constructType(AdminStatsResponse.class), 20, 4));
    }

    private static CacheDescriptor<Object> descriptor(String name, JavaType type, int ttlSeconds, long l1MiB) {
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4), l1MiB * 1024 * 1024, 1024 * 1024, true, false);
    }
}
