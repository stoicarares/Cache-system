package com.project.cacheEvict.impl.v2;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.api.EvictionPolicy;
import com.project.cacheEvict.metrics.CacheMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class TwoLevelCacheProxy<K, V> implements DataProvider<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(TwoLevelCacheProxy.class);

    private final DataProvider<K, V> wrappedProvider;
    private final DiskCacheRepository l2Cache;

    private final Map<K, V> l1CacheStorage;
    private final EvictionPolicy<K> l1EvictionPolicy;
    private final int l1MaxSize;

    private final CacheMetricsService metricsService;
    private final String cacheName = "v2_proxy_cache";

    private final ReentrantLock lock = new ReentrantLock();

    public TwoLevelCacheProxy(DataProvider<K, V> wrappedProvider,
                              DiskCacheRepository l2Cache,
                              EvictionPolicy<K> l1EvictionPolicy,
                              int l1MaxSize,
                              CacheMetricsService metricsService) {
        this.wrappedProvider = wrappedProvider;
        this.l2Cache = l2Cache;
        this.l1EvictionPolicy = l1EvictionPolicy;
        this.l1MaxSize = l1MaxSize;
        this.l1CacheStorage = new ConcurrentHashMap<>(l1MaxSize);
        this.metricsService = metricsService;
    }

    @Override
    public V get(K key) {
        // ... (Codul get rămâne neschimbat, vezi versiunea anterioară pentru detalii) ...
        // Pentru brevetate, am inclus doar metodele noi mai jos, dar clasa trebuie să fie completă.
        // --- LOGICA EXISTENTĂ PENTRU GET ---
        V value = l1CacheStorage.get(key);
        if (value != null) {
            metricsService.recordCacheRequest(cacheName, "hit_l1");
            recordL1Access(key);
            return value;
        }

        lock.lock();
        try {
            value = l1CacheStorage.get(key);
            if (value != null) {
                metricsService.recordCacheRequest(cacheName, "hit_l1");
                recordL1Access(key);
                return value;
            }

            String diskValue = l2Cache.load(key.toString());
            if (diskValue != null) {
                metricsService.recordCacheRequest(cacheName, "hit_l2");
                logger.info("V2: Promovare din L2 în L1 pentru cheia: {}", key);
                V castValue = (V) diskValue;
                addToL1(key, castValue);
                return castValue;
            }

            metricsService.recordCacheRequest(cacheName, "miss");
            logger.info("V2: Cache MISS. Accesare DB pentru cheia: {}", key);

            V newValue = wrappedProvider.get(key);

            if (newValue != null) {
                l2Cache.save(key.toString(), newValue.toString());
                addToL1(key, newValue);
            }
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    private void recordL1Access(K key) {
        lock.lock();
        try {
            l1EvictionPolicy.keyAccessed(key);
        } finally {
            lock.unlock();
        }
    }

    private void addToL1(K key, V value) {
        if (l1CacheStorage.size() >= l1MaxSize) {
            K keyToEvict = l1EvictionPolicy.getKeyToEvict();
            if (keyToEvict != null) {
                l1CacheStorage.remove(keyToEvict);
            }
        }
        l1CacheStorage.put(key, value);
        l1EvictionPolicy.keyAccessed(key);
    }

    // --- METODE NOI PENTRU EVICTION ---

    /**
     * Șterge DOAR cache-ul din memorie (L1).
     * Util pentru a testa promovarea din L2 sau comportamentul de restart.
     */
    public void clearL1() {
        lock.lock();
        try {
            l1CacheStorage.clear();
            l1EvictionPolicy.clear();
            logger.info("V2: L1 Cache (Memory) cleared.");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Șterge DOAR cache-ul de pe disc (L2).
     */
    public void clearL2() {
        // DiskCacheRepository are deja o metodă clearAll
        l2Cache.clearAll();
        logger.info("V2: L2 Cache (Disk) cleared.");
    }
}