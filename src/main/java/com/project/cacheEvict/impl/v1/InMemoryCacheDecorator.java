package com.project.cacheEvict.impl.v1;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.api.EvictionPolicy;
import com.project.cacheEvict.metrics.CacheMetricsService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryCacheDecorator<K, V> implements DataProvider<K, V> {
    private final DataProvider<K, V> wrappedProvider;
    private final EvictionPolicy<K> evictionPolicy;
    private final int maxSize;
    private final Map<K, V> cacheStorage;
    private final ReentrantLock lock = new ReentrantLock();

    private final CacheMetricsService metricsService;
    private final String cacheName;

    public InMemoryCacheDecorator(DataProvider<K, V> wrappedProvider,
                                  EvictionPolicy<K> evictionPolicy,
                                  int maxSize,
                                  CacheMetricsService metricsService,
                                  String cacheName) {
        this.wrappedProvider = wrappedProvider;
        this.evictionPolicy = evictionPolicy;
        this.maxSize = maxSize;
        this.cacheStorage = new ConcurrentHashMap<>(maxSize);
        this.metricsService = metricsService;
        this.cacheName = cacheName;
    }

    @Override
    public V get(K key) {
        V value = cacheStorage.get(key);

        if (value != null) {
            metricsService.recordCacheRequest(cacheName, "hit");
            lock.lock();
            try {
                evictionPolicy.keyAccessed(key);
            } finally {
                lock.unlock();
            }
            return value;
        }

        metricsService.recordCacheRequest(cacheName, "miss");

        lock.lock();
        try {
            value = cacheStorage.get(key);
            if (value != null) {
                evictionPolicy.keyAccessed(key);
                return value;
            }

            V newValue = wrappedProvider.get(key);

            if (newValue != null) {
                if (cacheStorage.size() >= maxSize) {
                    K keyToEvict = evictionPolicy.getKeyToEvict();
                    if (keyToEvict != null) {
                        cacheStorage.remove(keyToEvict);
                    }
                }
                cacheStorage.put(key, newValue);
                evictionPolicy.keyAccessed(key);
            }
            return newValue;

        } finally {
            lock.unlock();
        }
    }
}