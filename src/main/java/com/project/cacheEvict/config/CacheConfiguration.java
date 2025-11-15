package com.project.cacheEvict.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.Meter;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.impl.policy.LruEvictionPolicy;
import com.project.cacheEvict.impl.v1.InMemoryCacheDecorator;
import com.project.cacheEvict.metrics.CacheMetricsService;

@Configuration
public class CacheConfiguration {

    private static final int CACHE_MAX_SIZE = 100;



    @Bean
    @Qualifier("v1_Cache")
    public DataProvider<String, String> v1CacheProvider(
            @Qualifier("v0_Database") DataProvider<String, String> v0Provider,
            CacheMetricsService metricsService) {

        return new InMemoryCacheDecorator<>(
                v0Provider,
                new LruEvictionPolicy<>(),
                CACHE_MAX_SIZE,
                metricsService,
                "v1_decorator_cache"
        );
    }

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
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}