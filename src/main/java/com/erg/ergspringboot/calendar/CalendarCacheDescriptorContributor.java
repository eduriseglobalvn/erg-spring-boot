package com.erg.ergspringboot.calendar;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import com.erg.ergspringboot.platform.cache.CacheDescriptor;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Cache metadata of the calendar module's own caches (the workspace cache is described in the platform contributor). */
@Component
public class CalendarCacheDescriptorContributor implements CacheDescriptorContributor {

    @Override
    public List<CacheDescriptor<?>> descriptors(ObjectMapper mapper) {
        // The analysed week is an in-process object graph (L1 only), too large for the 1 MiB L2 entry limit.
        return List.of(
                descriptor(CalendarCaches.OVERVIEW_WEEK, mapper.constructType(Object.class), Duration.ofSeconds(20), 64),
                descriptor(CalendarCaches.CATALOG, mapper.constructType(CalendarCatalogResponse.class), Duration.ofSeconds(60), 32));
    }

    private static CacheDescriptor<Object> descriptor(String name, tools.jackson.databind.JavaType type, Duration ttl, long l1MiB) {
        return new CacheDescriptor<>(name, type, ttl, ttl.multipliedBy(4), l1MiB * 1024 * 1024, 1024 * 1024, true, false);
    }
}
