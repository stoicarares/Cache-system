package com.project.cacheEvict.test;

import com.project.cacheEvict.api.DataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock database for testing that simulates slow data access without requiring a real database.
 * Generates deterministic data based on keys and tracks access statistics.
 */
public class MockDatabaseDataProvider implements DataProvider<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(MockDatabaseDataProvider.class);

    private final Map<String, String> dataStore;
    private final long delayMs;
    private final int objectSizeBytes;
    private final AtomicLong accessCount;

    /**
     * @param delayMs Simulated database access delay in milliseconds
     * @param objectSizeBytes Size of generated objects in bytes (approximate)
     */
    public MockDatabaseDataProvider(long delayMs, int objectSizeBytes) {
        this.dataStore = new ConcurrentHashMap<>();
        this.delayMs = delayMs;
        this.objectSizeBytes = objectSizeBytes;
        this.accessCount = new AtomicLong(0);
    }

    @Override
    public String get(String key) {
        accessCount.incrementAndGet();

        // Simulate slow database operation
        simulateSlowOperation();

        // Return cached data or generate new data
        return dataStore.computeIfAbsent(key, this::generateData);
    }

    /**
     * Pre-populate the mock database with a specific number of keys
     */
    public void populateData(int numKeys) {
        // Silent population - no logging
        for (int i = 0; i < numKeys; i++) {
            String key = "key_" + i;
            dataStore.put(key, generateData(key));
        }
    }

    /**
     * Generate data of approximately the configured size
     */
    private String generateData(String key) {
        StringBuilder sb = new StringBuilder();
        sb.append("DATA_FOR_").append(key).append(":");

        // Fill to approximate target size
        int currentSize = sb.length();
        int remaining = objectSizeBytes - currentSize;

        if (remaining > 0) {
            String padding = "X".repeat(Math.max(0, remaining));
            sb.append(padding);
        }

        return sb.toString();
    }

    private void simulateSlowOperation() {
        if (delayMs > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public long getAccessCount() {
        return accessCount.get();
    }

    public void resetAccessCount() {
        accessCount.set(0);
    }

    public void clearData() {
        dataStore.clear();
        accessCount.set(0);
    }

    public int getDataStoreSize() {
        return dataStore.size();
    }
}