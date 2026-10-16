package com.erg.ergspringboot.config;

import com.erg.ergspringboot.platform.cache.CacheDescriptorRegistry;
import com.erg.ergspringboot.platform.cache.CacheDescriptorContributor;
import com.erg.ergspringboot.platform.cache.CacheFailurePolicy;
import com.erg.ergspringboot.platform.cache.CacheKeyFactory;
import com.erg.ergspringboot.platform.cache.CacheLoadCoordinator;
import com.erg.ergspringboot.platform.cache.CacheTenantResolver;
import com.erg.ergspringboot.platform.cache.ErgCacheProperties;
import com.erg.ergspringboot.platform.cache.ErgTieredCacheManager;
import com.erg.ergspringboot.platform.cache.JacksonCacheValueCodec;
import com.erg.ergspringboot.platform.cache.LocalCacheGenerationService;
import com.erg.ergspringboot.platform.cache.CacheGenerationService;
import com.erg.ergspringboot.platform.cache.CacheInvalidationService;
import com.erg.ergspringboot.platform.cache.BusinessCacheRuntimeHints;
import com.erg.ergspringboot.platform.cache.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import com.erg.ergspringboot.platform.cache.NoOpRedisCacheStore;
import com.erg.ergspringboot.platform.cache.RedisCacheStore;
import com.erg.ergspringboot.platform.security.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableCaching
@EnableConfigurationProperties(ErgCacheProperties.class)
@ImportRuntimeHints({CaffeineNativeRuntimeHints.class, BusinessCacheRuntimeHints.class})
public class CacheConfig {

    @Bean
    ErgTieredCacheManager cacheManager(
            ObjectMapper objectMapper,
            ErgCacheProperties properties,
            ObjectProvider<RedisCacheStore> redisStore,
            CacheGenerationService generations,
            CacheInvalidationService invalidations,
            CacheMetrics metrics,
            ObjectProvider<CacheDescriptorContributor> contributors
    ) {
        return build(objectMapper, properties,
                redisStore.getIfAvailable(() -> NoOpRedisCacheStore.INSTANCE), generations, invalidations, metrics,
                contributors.orderedStream().sorted(Comparator.comparing(x -> x.getClass().getName())).toList());
    }

    @Bean
    CacheMetrics cacheMetrics(MeterRegistry registry) {
        return new CacheMetrics(registry);
    }

    public CacheManager cacheManager() {
        return build(JsonMapper.builder().findAndAddModules().build(),
                new ErgCacheProperties(), NoOpRedisCacheStore.INSTANCE,
                new LocalCacheGenerationService(), null, null, List.of(
                        new LmsCacheDescriptorContributor(), new PlatformCacheDescriptorContributor()));
    }

    private ErgTieredCacheManager build(ObjectMapper mapper, ErgCacheProperties properties,
            RedisCacheStore store, CacheGenerationService generations, CacheInvalidationService invalidations,
            CacheMetrics metrics, Collection<? extends CacheDescriptorContributor> contributors) {
        CacheDescriptorRegistry descriptors = new CacheDescriptorRegistry(descriptors(mapper, contributors));
        CacheTenantResolver tenants = () -> {
            TenantContext context = TenantContext.current();
            return context == null ? null : context.tenantId();
        };
        return new ErgTieredCacheManager(
                descriptors,
                new CacheKeyFactory(mapper),
                new JacksonCacheValueCodec(mapper),
                store,
                tenants,
                generations,
                new CacheFailurePolicy(properties.getCircuitFailureThreshold(), properties.getCircuitOpenDuration()),
                new CacheLoadCoordinator(properties.getMaxInFlightLoads()),
                properties.isL2Enabled() ? properties.getL2Names() : java.util.Set.of(),
                invalidations,
                metrics
        );
    }

    private List<com.erg.ergspringboot.platform.cache.CacheDescriptor<?>> descriptors(
            ObjectMapper mapper, Collection<? extends CacheDescriptorContributor> contributors) {
        List<com.erg.ergspringboot.platform.cache.CacheDescriptor<?>> descriptors = new ArrayList<>();
        for (CacheDescriptorContributor contributor : contributors) {
            descriptors.addAll(contributor.descriptors(mapper));
        }
        return descriptors;
    }
}
