package com.project.cacheEvict.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CacheMetricsService {
    @Autowired
    private final MeterRegistry meterRegistry;

    public CacheMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCacheRequest(String cacheName, String result) {
        meterRegistry.counter("cache.requests", "name", cacheName, "result", result).increment();
    }
}