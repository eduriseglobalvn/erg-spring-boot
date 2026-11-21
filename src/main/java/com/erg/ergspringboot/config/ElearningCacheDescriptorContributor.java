package com.erg.ergspringboot.config;

import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Cache metadata owned by the student e-learning module (the per-student tier caches stay with LMS). */
@Component
public class ElearningCacheDescriptorContributor implements CacheDescriptorContributor {
    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        Duration ttl = Duration.ofSeconds(20);
        // Class-shared part of a discussion feed page, key {tenantId, classId, ...}: scoped by class so a post, comment,
        // reaction or moderation write evicts only that class (StudentDiscussionFeedCache.evictClass).
        return List.of(new CacheDescriptor<>("elearningDiscussionFeed", mapper.constructType(DiscussionFeedSnapshot.class), ttl,
                ttl.multipliedBy(4), 64L * 1024 * 1024, 1024 * 1024, true, false, LmsCacheDescriptorContributor.keyPart(1)));
    }
}
