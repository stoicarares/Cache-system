package com.project.cacheEvict.test;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.api.EvictionPolicy;
import com.project.cacheEvict.impl.policy.LruEvictionPolicy;
import com.project.cacheEvict.impl.v2.DiskCacheRepository;
import com.project.cacheEvict.impl.v2.TwoLevelCacheProxy;
import com.project.cacheEvict.metrics.CacheMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Unit tests for V2 Two-Level Cache behavior verification.
 * Tests L1 (memory) and L2 (disk) cache interaction, eviction, and promotion.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TwoLevelCacheTest {
    
    private Path tempCacheDir;
    private MockDatabaseDataProvider mockDb;
    private DiskCacheRepository l2Cache;
    private TwoLevelCacheProxy<String, String> cacheProxy;
    
    @BeforeAll
    public void setupGlobal() throws IOException {
        tempCacheDir = Files.createTempDirectory("test_l2_cache");
    }
    
    @AfterAll
    public void cleanupGlobal() throws IOException {
        if (tempCacheDir != null && Files.exists(tempCacheDir)) {
            Files.walk(tempCacheDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) {}
                    });
        }
    }
    
    @BeforeEach
    public void setup() throws IOException {
        // Create fresh mock database
        mockDb = new MockDatabaseDataProvider(10, 1024);
        mockDb.populateData(100);
        
        // Create L2 cache repository
        Path l2Dir = Files.createTempDirectory(tempCacheDir, "l2_");
        l2Cache = new DiskCacheRepository(l2Dir.toString(), 50);
        
        // Create two-level cache proxy with small L1 (10 entries)
        CacheMetricsService metrics = new CacheMetricsService(new SimpleMeterRegistry());
        EvictionPolicy<String> policy = new LruEvictionPolicy<>();
        cacheProxy = new TwoLevelCacheProxy<>(mockDb, l2Cache, policy, 10, metrics);
    }
    
    @Test
    @DisplayName("Test 1: L1 Cache Hit")
    public void test1_L1CacheHit() {
        // First access - should hit database
        long dbAccessBefore = mockDb.getAccessCount();
        String value1 = cacheProxy.get("key_1");
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertNotNull(value1);
        Assertions.assertEquals(1, dbAccessAfter - dbAccessBefore, "First access should hit database");
        
        // Second access - should hit L1
        dbAccessBefore = mockDb.getAccessCount();
        String value2 = cacheProxy.get("key_1");
        dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(0, dbAccessAfter - dbAccessBefore, "Second access should hit L1 cache");
    }
    
    @Test
    @DisplayName("Test 2: L1 Eviction and L2 Storage")
    public void test2_L1EvictionAndL2Storage() {
        // Fill L1 cache (capacity is 10)
        for (int i = 0; i < 10; i++) {
            cacheProxy.get("key_" + i);
        }
        
        long dbAccessBefore = mockDb.getAccessCount();
        
        // Access 11th key - should trigger L1 eviction
        cacheProxy.get("key_10");
        
        // First evicted key should still be in L2
        // Access it again - should NOT hit database (L2 hit)
        String evictedValue = cacheProxy.get("key_0");
        
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertNotNull(evictedValue);
        // Should have 1 DB access for key_10, but key_0 should be in L2
        Assertions.assertEquals(1, dbAccessAfter - dbAccessBefore, 
                               "Evicted key should be retrieved from L2");
    }
    
    @Test
    @DisplayName("Test 3: L2 Promotion to L1")
    public void test3_L2PromotionToL1() {
        // Fill L1 and trigger eviction
        for (int i = 0; i < 15; i++) {
            cacheProxy.get("key_" + i);
        }
        
        // Clear L1 only
        cacheProxy.clearL1();
        
        // Access a key that should be in L2
        long dbAccessBefore = mockDb.getAccessCount();
        String value1 = cacheProxy.get("key_5");
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertNotNull(value1);
        // Should NOT hit database (should be in L2)
        Assertions.assertEquals(0, dbAccessAfter - dbAccessBefore, 
                               "Should retrieve from L2 without database access");
        
        // Access again - should now be in L1 (promoted)
        dbAccessBefore = mockDb.getAccessCount();
        String value2 = cacheProxy.get("key_5");
        dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(0, dbAccessAfter - dbAccessBefore, 
                               "Promoted key should be in L1");
    }
    
    @Test
    @DisplayName("Test 4: L2 Disk Persistence")
    public void test4_L2DiskPersistence() {
        // Store data in cache
        String key = "key_persistent";
        String value1 = cacheProxy.get(key);
        Assertions.assertNotNull(value1);
        
        // Clear L1 only
        cacheProxy.clearL1();
        
        // Verify L2 still has the data (no DB access)
        long dbAccessBefore = mockDb.getAccessCount();
        String value2 = cacheProxy.get(key);
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(0, dbAccessAfter - dbAccessBefore, 
                               "L2 should persist data after L1 clear");
    }
    
    @Test
    @DisplayName("Test 5: Complete Cache Clear")
    public void test5_CompleteCacheClear() {
        // Store data
        String value1 = cacheProxy.get("key_clear");
        Assertions.assertNotNull(value1);
        
        // Clear both L1 and L2
        cacheProxy.clearL1();
        cacheProxy.clearL2();
        
        // Access should hit database
        long dbAccessBefore = mockDb.getAccessCount();
        String value2 = cacheProxy.get("key_clear");
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(1, dbAccessAfter - dbAccessBefore, 
                               "After complete clear, should hit database");
    }
    
    @Test
    @DisplayName("Test 6: L2 Capacity and Eviction")
    public void test6_L2CapacityAndEviction() {
        // L2 capacity is 50 files
        // Fill it beyond capacity
        for (int i = 0; i < 60; i++) {
            cacheProxy.get("key_" + i);
        }
        
        // Clear L1 to force L2 access
        cacheProxy.clearL1();
        
        // First keys should have been evicted from L2
        long dbAccessBefore = mockDb.getAccessCount();
        cacheProxy.get("key_0");
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(1, dbAccessAfter - dbAccessBefore, 
                               "Oldest L2 entries should be evicted");
        
        // Recent keys should still be in L2
        dbAccessBefore = mockDb.getAccessCount();
        cacheProxy.get("key_55");
        dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(0, dbAccessAfter - dbAccessBefore, 
                               "Recent L2 entries should still exist");
    }
    
    @Test
    @DisplayName("Test 7: LRU Behavior in L1")
    public void test7_LRUBehaviorInL1() {
        // Fill L1 (capacity 10)
        for (int i = 0; i < 10; i++) {
            cacheProxy.get("key_" + i);
        }
        
        // Access key_0 again (make it most recently used)
        cacheProxy.get("key_0");
        
        // Add new key (should evict key_1, not key_0)
        cacheProxy.get("key_new");
        
        // Clear L2 to isolate L1 behavior
        cacheProxy.clearL2();
        
        // key_0 should still be in L1 (no DB access)
        long dbAccessBefore = mockDb.getAccessCount();
        cacheProxy.get("key_0");
        long dbAccessAfter = mockDb.getAccessCount();
        
        Assertions.assertEquals(0, dbAccessAfter - dbAccessBefore, 
                               "LRU should keep recently accessed keys in L1");
    }
    
    @Test
    @DisplayName("Test 8: Concurrent Access")
    public void test8_ConcurrentAccess() throws InterruptedException {
        // Access from multiple threads
        int numThreads = 4;
        int accessesPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < accessesPerThread; i++) {
                    String key = "key_" + ((threadId * accessesPerThread + i) % 50);
                    String value = cacheProxy.get(key);
                    Assertions.assertNotNull(value);
                }
            });
            threads[t].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify no errors occurred and cache is functional
        String testValue = cacheProxy.get("key_1");
        Assertions.assertNotNull(testValue);
    }
}
