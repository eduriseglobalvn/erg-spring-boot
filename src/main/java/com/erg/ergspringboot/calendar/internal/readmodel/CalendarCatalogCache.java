package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.CalendarCaches;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarCatalogResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Short-lived cache of the filter catalog (about 11 queries) that every workspace read used to rebuild. The key is every
 * input of the catalog (tenant, schools, range) and nothing about the caller, because the catalog does not depend on
 * the caller; every calendar write evicts it through {@code @CalendarCacheEvict}.
 */
@Service
class CalendarCatalogCache {

    @Cacheable(cacheNames = CalendarCaches.CATALOG, key = "{#tenantId, "
            + "(#schoolIds == null ? '*' : new java.util.TreeSet(#schoolIds)), #from, #to}")
    public CalendarCatalogResponse catalog(String tenantId, List<String> schoolIds, OffsetDateTime from, OffsetDateTime to,
            CalendarWorkspaceQueryUseCase.CatalogLoader loader) {
        return loader.load(tenantId, schoolIds, from, to);
    }
}
