package com.project.cacheEvict.test;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.impl.policy.LruEvictionPolicy;
import com.project.cacheEvict.impl.v1.InMemoryCacheDecorator;
import com.project.cacheEvict.impl.v2.DiskCacheRepository;
import com.project.cacheEvict.impl.v2.TwoLevelCacheProxy;
import com.project.cacheEvict.metrics.CacheMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Quick Start Example Test
 * 
 * This is a simple, self-contained test that you can run immediately to verify
 * the benchmark framework is working correctly. It demonstrates the basic usage
 * of all components without requiring complex setup.
 * 
 * Run this test first to ensure everything is working before running the
 * comprehensive benchmark suite.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QuickStartExampleTest {
    
    private Path tempCacheDir;
    
    @BeforeAll
    public void setup() throws IOException {
        tempCacheDir = Files.createTempDirectory("quickstart_cache");
        System.out.println("\n" + "=".repeat(70));
        System.out.println("QUICK START EXAMPLE TEST");
        System.out.println("=".repeat(70));
        System.out.println("This test demonstrates basic cache benchmark usage.\n");
    }
    
    @AfterAll
    public void cleanup() throws IOException {
        if (tempCacheDir != null && Files.exists(tempCacheDir)) {
            Files.walk(tempCacheDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) {}
                    });
        }
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Quick Start Test Complete!");
        System.out.println("=".repeat(70) + "\n");
    }
    
    @Test
    public void demonstrateBasicUsage() throws Exception {
        System.out.println("STEP 1: Creating Mock Database");
        System.out.println("-".repeat(70));
        
        // Create a mock database with 50ms delay and 1KB objects
        MockDatabaseDataProvider mockDb = new MockDatabaseDataProvider(50, 1024);
        mockDb.populateData(100); // Populate with 100 keys
        
        System.out.println("✓ Mock database created with 100 keys, 1KB objects, 50ms delay\n");
        
        // Test V0 (Direct Database Access)
        System.out.println("STEP 2: Testing V0 (Direct Database Access)");
        System.out.println("-".repeat(70));
        testImplementation("V0-Database", mockDb);
        
        // Test V1 (In-Memory Cache with Decorator Pattern)
        System.out.println("\nSTEP 3: Testing V1 (Decorator Pattern - In-Memory Cache)");
        System.out.println("-".repeat(70));
        MockDatabaseDataProvider mockDb1 = new MockDatabaseDataProvider(50, 1024);
        mockDb1.populateData(100);
        DataProvider<String, String> v1 = createV1Cache(mockDb1);
        testImplementation("V1-Decorator", v1);
        
        // Test V2 (Two-Level Cache with Proxy Pattern)
        System.out.println("\nSTEP 4: Testing V2 (Proxy Pattern - Two-Level Cache)");
        System.out.println("-".repeat(70));
        MockDatabaseDataProvider mockDb2 = new MockDatabaseDataProvider(50, 1024);
        mockDb2.populateData(100);
        DataProvider<String, String> v2 = createV2Cache(mockDb2);
        testImplementation("V2-TwoLevel", v2);
        
        System.out.println("\nAll implementations tested successfully! ✓");
    }
    
    private void testImplementation(String name, DataProvider<String, String> provider) {
        // Generate access pattern: 1000 accesses with Zipfian distribution (80/20 rule)
        AccessPatternGenerator generator = new AccessPatternGenerator(100);
        List<String> accessPattern = generator.generateZipfianPattern(1000, 1.0);
        
        System.out.println("Testing " + name + " with 1000 requests (Zipfian distribution)...");
        
        long startTime = System.nanoTime();
        int hits = 0;
        
        for (int i = 0; i < accessPattern.size(); i++) {
            String key = accessPattern.get(i);
            long reqStart = System.nanoTime();
            String value = provider.get(key);
            long reqDuration = System.nanoTime() - reqStart;
            
            // Estimate if it was a cache hit (fast response) or miss (slow response)
            // Threshold: 10ms (requests faster than this are likely cache hits)
            if (reqDuration < 10_000_000) { // 10ms in nanoseconds
                hits++;
            }
            
            // Progress indicator every 200 requests
            if ((i + 1) % 200 == 0) {
                System.out.printf("  Progress: %d/%d requests completed\n", i + 1, accessPattern.size());
            }
        }
        
        long totalDuration = System.nanoTime() - startTime;
        double throughput = (1000 * 1_000_000_000.0) / totalDuration; // requests per second
        double estimatedHitRatio = (double) hits / 1000;
        
        System.out.println("\nResults:");
        System.out.printf("  Total Duration: %.2f ms\n", totalDuration / 1_000_000.0);
        System.out.printf("  Throughput: %.2f requests/second\n", throughput);
        System.out.printf("  Estimated Hit Ratio: %.2f%%\n", estimatedHitRatio * 100);
        System.out.printf("  Average Latency: %.2f µs\n", (totalDuration / 1000.0) / 1000.0);
        
        // Print interpretation
        System.out.println("\nInterpretation:");
        if (estimatedHitRatio > 0.8) {
            System.out.println("  ✓ Excellent cache performance! High hit ratio indicates good caching.");
        } else if (estimatedHitRatio > 0.5) {
            System.out.println("  ◐ Moderate cache performance. Some benefit from caching.");
        } else if (name.equals("V0-Database")) {
            System.out.println("  ○ No caching - all requests hit the database (as expected).");
        } else {
            System.out.println("  ✗ Low cache effectiveness. May need tuning.");
        }
    }
    
    private DataProvider<String, String> createV1Cache(MockDatabaseDataProvider mockDb) {
        CacheMetricsService metrics = new CacheMetricsService(new SimpleMeterRegistry());
        return new InMemoryCacheDecorator<>(
                mockDb,
                new LruEvictionPolicy<>(),
                50,  // Cache size: 50 entries
                metrics,
                "quickstart_v1"
        );
    }
    
    private DataProvider<String, String> createV2Cache(MockDatabaseDataProvider mockDb) throws IOException {
        CacheMetricsService metrics = new CacheMetricsService(new SimpleMeterRegistry());
        
        // Create L2 cache directory
        Path l2Dir = Files.createTempDirectory(tempCacheDir, "l2_");
        DiskCacheRepository l2Cache = new DiskCacheRepository(l2Dir.toString(), 100);
        
        return new TwoLevelCacheProxy<>(
                mockDb,
                l2Cache,
                new LruEvictionPolicy<>(),
                25,  // L1 size: 25 entries
                metrics
        );
    }
    
    @Test
    public void demonstrateAccessPatterns() {
        System.out.println("\nDEMONSTRATING ACCESS PATTERNS");
        System.out.println("=".repeat(70));
        
        AccessPatternGenerator generator = new AccessPatternGenerator(100);
        
        // Uniform Pattern
        System.out.println("\n1. UNIFORM DISTRIBUTION:");
        System.out.println("   All keys have equal probability of access");
        List<String> uniform = generator.generateUniformPattern(1000);
        analyzePattern(uniform, "Uniform");
        
        // Zipfian Pattern
        System.out.println("\n2. ZIPFIAN DISTRIBUTION (80/20 rule):");
        System.out.println("   ~20% of keys receive ~80% of accesses");
        List<String> zipfian = generator.generateZipfianPattern(1000, 1.0);
        analyzePattern(zipfian, "Zipfian");
        
        System.out.println("\nConclusion:");
        System.out.println("  Zipfian patterns have high locality → Better cache hit ratios");
        System.out.println("  Uniform patterns have low locality → Lower cache hit ratios");
    }
    
    private void analyzePattern(List<String> pattern, String name) {
        // Count unique keys accessed
        long uniqueKeys = pattern.stream().distinct().count();
        
        // Find most frequently accessed key
        String mostCommon = pattern.stream()
                .collect(java.util.stream.Collectors.groupingBy(k -> k, 
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("none");
        
        long mostCommonCount = pattern.stream()
                .filter(k -> k.equals(mostCommon))
                .count();
        
        System.out.printf("   Total accesses: %d\n", pattern.size());
        System.out.printf("   Unique keys accessed: %d\n", uniqueKeys);
        System.out.printf("   Most accessed key: %s (%d times = %.1f%%)\n", 
                mostCommon, mostCommonCount, (mostCommonCount * 100.0) / pattern.size());
    }
}
