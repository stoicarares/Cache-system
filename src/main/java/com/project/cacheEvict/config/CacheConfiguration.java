package com.project.cacheEvict.config;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.impl.policy.LruEvictionPolicy;
import com.project.cacheEvict.impl.v1.InMemoryCacheDecorator;
import com.project.cacheEvict.impl.v2.DiskCacheRepository;
import com.project.cacheEvict.impl.v2.TwoLevelCacheProxy;
import com.project.cacheEvict.metrics.CacheMetricsService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfiguration {

    private static final int L1_CACHE_SIZE = 50; // L1 e mai mic în V2 (doar date fierbinți)

    // --- Bean-uri existente (V0, V1, Metrics) rămân neschimbate ---

    @Bean
    public MeterFilter filterOnlyCacheMetrics() {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (id.getName().startsWith("cache.")) {
                    return MeterFilterReply.ACCEPT;
                }
                return MeterFilterReply.DENY;
            }
        };
    }

    @Bean
    @Qualifier("v1_Cache")
    public DataProvider<String, String> v1CacheProvider(
            @Qualifier("v0_Database") DataProvider<String, String> v0Provider,
            CacheMetricsService metricsService) {

        return new InMemoryCacheDecorator<>(
                v0Provider,
                new LruEvictionPolicy<>(),
                100, // V1 size
                metricsService,
                "v1_decorator_cache"
        );
    }

    // --- SPRINT 2: Adăugare Bean V2 ---

    @Bean
    @Qualifier("v2_Cache")
    public DataProvider<String, String> v2CacheProvider(
            @Qualifier("v0_Database") DataProvider<String, String> v0Provider,
            DiskCacheRepository diskCacheRepository, // Injectăm L2
            CacheMetricsService metricsService) {

        return new TwoLevelCacheProxy<>(
                v0Provider,
                diskCacheRepository,
                new LruEvictionPolicy<>(),
                L1_CACHE_SIZE,
                metricsService
        );
    }
}