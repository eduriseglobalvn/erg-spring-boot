package com.erg.ergspringboot.timekeeping.internal.schedule;

import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Cache metadata owned by timekeeping: the per-school setup read on every punch and day view. */
@Component
class TimekeepingCacheDescriptorContributor implements CacheDescriptorContributor {

    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        Duration ttl = Duration.ofSeconds(60);
        // key {tenant, school}: element 1 is the scope, so a config write clears one school (SchoolSetupCache.evict)
        return List.of(new CacheDescriptor<>(SchoolSetupCache.CACHE, mapper.constructType(SchoolSetup.class), ttl,
                ttl.multipliedBy(4), 8L * 1024 * 1024, 256 * 1024, true, false,
                key -> key instanceof List<?> parts && parts.size() > 1 && parts.get(1) != null
                        ? parts.get(1).toString() : null));
    }
}
