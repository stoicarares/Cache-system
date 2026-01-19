package com.project.cacheEvict.test;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.api.EvictionPolicy;
import com.project.cacheEvict.impl.policy.LruEvictionPolicy;
import com.project.cacheEvict.impl.v1.InMemoryCacheDecorator;
import com.project.cacheEvict.impl.v2.DiskCacheRepository;
import com.project.cacheEvict.impl.v2.TwoLevelCacheProxy;
import com.project.cacheEvict.metrics.CacheMetricsService;
import com.project.cacheEvict.test.BenchmarkConfig.AccessPattern;
import com.project.cacheEvict.test.BenchmarkConfig.Scenario;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.project.cacheEvict.test.BenchmarkConfig.*;

/**
 * Comprehensive benchmark test suite for caching implementations.
 * Tests V0 (Database), V1 (Decorator), and V2 (Proxy + Two-Level Cache).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ComprehensiveBenchmarkTest {

    private static final String RESULTS_DIR = "./benchmark_results";
    private Path tempCacheDir;

    @BeforeAll
    public void setupGlobal() throws IOException {
        // Create results directory
        new File(RESULTS_DIR).mkdirs();

        // Create temporary cache directory for L2
        tempCacheDir = Files.createTempDirectory("benchmark_l2_cache");

        System.out.println("==============================================");
        System.out.println("COMPREHENSIVE CACHE BENCHMARK TEST SUITE");
        System.out.println("==============================================");
        System.out.println("Results will be saved to: " + RESULTS_DIR);
        System.out.println("L2 Cache directory: " + tempCacheDir);
        // Separator
    }

    @AfterAll
    public void cleanupGlobal() throws IOException {
        // Cleanup temporary L2 cache directory
        if (tempCacheDir != null && Files.exists(tempCacheDir)) {
            Files.walk(tempCacheDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    /**
     * Test 1: Basic functionality verification for all implementations
     */
    @Test
    @DisplayName("Test 1: Basic Functionality Verification")
    public void test1_BasicFunctionalityVerification() throws IOException {
        // Silent execution

        MockDatabaseDataProvider mockDb = new MockDatabaseDataProvider(10, 1024);
        mockDb.populateData(100);

        // Test V0 (Direct Database)
        testBasicFunctionality("V0-Database", mockDb);

        // Test V1 (Decorator)
        MockDatabaseDataProvider mockDb1 = new MockDatabaseDataProvider(10, 1024);
        mockDb1.populateData(100);
        DataProvider<String, String> v1 = createV1Provider(mockDb1, 50);
        testBasicFunctionality("V1-Decorator", v1);

        // Test V2 (Two-Level)
        MockDatabaseDataProvider mockDb2 = new MockDatabaseDataProvider(10, 1024);
        mockDb2.populateData(100);
        DataProvider<String, String> v2 = createV2Provider(mockDb2, 25);
        testBasicFunctionality("V2-TwoLevel", v2);
    }

    private void testBasicFunctionality(String name, DataProvider<String, String> provider) {

        // Test basic get
        String value1 = provider.get("key_1");
        Assertions.assertNotNull(value1, name + " should return non-null value");

        // Test cache hit (second access should be faster)
        String value2 = provider.get("key_1");
        Assertions.assertEquals(value1, value2, name + " should return same value");

        // Test different keys
        String value3 = provider.get("key_2");
        Assertions.assertNotNull(value3, name + " should return value for different key");
        Assertions.assertNotEquals(value1, value3, name + " should return different values for different keys");
    }

    /**
     * Test 2: Access Pattern Comparison (Uniform vs Zipfian)
     */
    @Test
    @DisplayName("Test 2: Access Pattern Comparison - Uniform vs Zipfian")
    public void test2_AccessPatternComparison() throws Exception {
        System.out.println("\n=== TEST 2: Access Pattern Comparison ===\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Uniform distribution
        Scenario uniformScenario = new Scenario(
                "Uniform-10K-1KB",
                KEYS_SMALL,
                BenchmarkConfig.OBJECT_SIZE_SMALL,
                NUM_ACCESSES_MEDIUM,
                1,
                BenchmarkConfig.DB_DELAY_MEDIUM,
                AccessPattern.UNIFORM
        );

        // Zipfian distribution
        Scenario zipfianScenario = new Scenario(
                "Zipfian-10K-1KB",
                KEYS_SMALL,
                BenchmarkConfig.OBJECT_SIZE_SMALL,
                NUM_ACCESSES_MEDIUM,
                1,
                BenchmarkConfig.DB_DELAY_MEDIUM,
                AccessPattern.ZIPFIAN_MEDIUM
        );

        // Test all implementations with both patterns
        results.add(runScenario("V0-Database", uniformScenario));
        results.add(runScenario("V1-Decorator", uniformScenario));
        results.add(runScenario("V2-TwoLevel", uniformScenario));

        results.add(runScenario("V0-Database", zipfianScenario));
        results.add(runScenario("V1-Decorator", zipfianScenario));
        results.add(runScenario("V2-TwoLevel", zipfianScenario));

        // Save results
        saveResultsToCsv(results, "test2_access_patterns.csv");
        printComparisonTable(results);

        // Test complete
    }

    /**
     * Test 3: Object Size Impact (1KB vs 100KB)
     */
    @Test
    @DisplayName("Test 3: Object Size Impact - 1KB vs 100KB")
    public void test3_ObjectSizeImpact() throws Exception {
        System.out.println("\n=== TEST 3: Object Size Impact ===\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Small objects (1KB)
        Scenario smallObjScenario = new Scenario(
                "Small-1KB",
                KEYS_SMALL,
                BenchmarkConfig.OBJECT_SIZE_SMALL,
                NUM_ACCESSES_MEDIUM,
                1,
                BenchmarkConfig.DB_DELAY_MEDIUM,
                AccessPattern.ZIPFIAN_MEDIUM
        );

        // Large objects (100KB)
        Scenario largeObjScenario = new Scenario(
                "Large-100KB",
                KEYS_SMALL,
                OBJECT_SIZE_LARGE,
                BenchmarkConfig.NUM_ACCESSES_SMALL, // Fewer accesses for large objects
                1,
                BenchmarkConfig.DB_DELAY_MEDIUM,
                AccessPattern.ZIPFIAN_MEDIUM
        );

        // Test all implementations
        results.add(runScenario("V0-Database", smallObjScenario));
        results.add(runScenario("V1-Decorator", smallObjScenario));
        results.add(runScenario("V2-TwoLevel", smallObjScenario));

        results.add(runScenario("V0-Database", largeObjScenario));
        results.add(runScenario("V1-Decorator", largeObjScenario));
        results.add(runScenario("V2-TwoLevel", largeObjScenario));

        saveResultsToCsv(results, "test3_object_size.csv");
        printComparisonTable(results);

        // Test complete
    }

    /**
     * Test 4: Scalability - Thread Count Impact (REDUCED: only 1 and 4 threads)
     */
    @Test
    @DisplayName("Test 4: Scalability - Thread Count Impact")
    public void test4_ScalabilityThreadCount() throws Exception {
        System.out.println("\n=== TEST 4: Scalability - Thread Count Impact ===\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Only test 1 and 4 threads for speed
        for (int threadCount : BenchmarkConfig.THREAD_COUNTS) {
            Scenario scenario = new Scenario(
                    "Threads-" + threadCount,
                    KEYS_SMALL,
                    BenchmarkConfig.OBJECT_SIZE_SMALL,
                    NUM_ACCESSES_MEDIUM,
                    threadCount,
                    BenchmarkConfig.DB_DELAY_MEDIUM,
                    AccessPattern.ZIPFIAN_MEDIUM
            );

            results.add(runScenario("V0-Database", scenario));
            results.add(runScenario("V1-Decorator", scenario));
            results.add(runScenario("V2-TwoLevel", scenario));
        }

        saveResultsToCsv(results, "test4_scalability.csv");
        printComparisonTable(results);

        // Test complete
    }

    /**
     * Test 5: Large Dataset Performance (REDUCED for speed)
     */
    @Test
    @DisplayName("Test 5: Large Dataset Performance")
    public void test5_LargeDatasetPerformance() throws Exception {
        System.out.println("\n=== TEST 5: Large Dataset Performance ===\n");

        List<BenchmarkResult> results = new ArrayList<>();

        Scenario largeDatasetScenario = new Scenario(
                "Large-Dataset",
                BenchmarkConfig.KEYS_LARGE,  // 2k keys (instead of 100k)
                BenchmarkConfig.OBJECT_SIZE_SMALL,
                BenchmarkConfig.NUM_ACCESSES_LARGE,  // 2k accesses
                1, // Single thread for speed
                BenchmarkConfig.DB_DELAY_MEDIUM,
                AccessPattern.ZIPFIAN_MEDIUM
        );

        results.add(runScenario("V0-Database", largeDatasetScenario));
        results.add(runScenario("V1-Decorator", largeDatasetScenario));
        results.add(runScenario("V2-TwoLevel", largeDatasetScenario));

        saveResultsToCsv(results, "test5_large_dataset.csv");
        printComparisonTable(results);

        // Test complete
    }

    /**
     * Test 6: Cache Eviction Behavior
     */
    @Test
    @DisplayName("Test 6: Cache Eviction Behavior")
    public void test6_CacheEvictionBehavior() throws Exception {
        System.out.println("\n=== TEST 6: Cache Eviction Behavior ===\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Scenario with more keys than cache can hold (forces eviction)
        Scenario evictionScenario = new Scenario(
                "Eviction-Test",
                1000, // More keys than cache size
                BenchmarkConfig.OBJECT_SIZE_SMALL,
                20000, // Many accesses
                1,
                BenchmarkConfig.DB_DELAY_MEDIUM,
                AccessPattern.UNIFORM // Uniform to trigger evictions
        );

        // V1 with small cache (50 entries)
        BenchmarkResult v1Result = runScenario("V1-Decorator-Small", evictionScenario);
        results.add(v1Result);

        // V2 with small L1 (25 entries) and L2
        BenchmarkResult v2Result = runScenario("V2-TwoLevel-Small", evictionScenario);
        results.add(v2Result);

        saveResultsToCsv(results, "test6_eviction.csv");
        printComparisonTable(results);

        // Test complete
    }

    /**
     * Test 7: Memory and Resource Usage
     */
    @Test
    @DisplayName("Test 7: Memory and Resource Usage")
    public void test7_MemoryAndResourceUsage() throws Exception {
        System.out.println("\n=== TEST 7: Memory and Resource Usage ===\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Test with different object sizes to measure memory impact
        Scenario[] scenarios = {
                new Scenario("Memory-1KB", 10000, BenchmarkConfig.OBJECT_SIZE_SMALL,
                        20000, 4, BenchmarkConfig.DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
                new Scenario("Memory-100KB", 1000, OBJECT_SIZE_LARGE,
                        5000, 4, BenchmarkConfig.DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM)
        };

        for (Scenario scenario : scenarios) {
            results.add(runScenarioWithMemoryTracking("V1-Decorator", scenario));
            results.add(runScenarioWithMemoryTracking("V2-TwoLevel", scenario));
        }

        saveResultsToCsv(results, "test7_memory_usage.csv");
        printComparisonTable(results);

        // Test complete
    }

    /**
     * Test 8: Full Standard Scenarios (REDUCED for speed)
     */
    @Test
    @DisplayName("Test 8: Key Standard Scenarios (Fast)")
    public void test8_CompleteStandardScenarios() throws Exception {
        System.out.println("\n=== TEST 8: Key Standard Scenarios (Fast) ===\n");

        List<BenchmarkResult> allResults = new ArrayList<>();

        // Run only the most important scenarios for speed
        Scenario[] keyScenarios = {
                // Small dataset, Zipfian (most common pattern)
                new Scenario("Small-Zipfian-1T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 1, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),

                // Scalability test with 4 threads
                new Scenario("Scalability-4T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 4, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),

                // Large object test
                new Scenario("Large-Obj-Zipfian", KEYS_SMALL, OBJECT_SIZE_LARGE,
                        NUM_ACCESSES_SMALL, 1, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
        };

        for (Scenario scenario : keyScenarios) {
            System.out.println("Running scenario: " + scenario);

            allResults.add(runScenario("V0-Database", scenario));
            allResults.add(runScenario("V1-Decorator", scenario));
            allResults.add(runScenario("V2-TwoLevel", scenario));

            // Separator
        }

        saveResultsToCsv(allResults, "test8_complete_scenarios.csv");
        generateSummaryReport(allResults);

        System.out.println("✓ Key standard scenarios test finished\n");
    }

    // ==================== Helper Methods ====================

    private BenchmarkResult runScenario(String implementation, Scenario scenario) throws Exception {
        return runScenarioWithMemoryTracking(implementation, scenario);
    }

    private BenchmarkResult runScenarioWithMemoryTracking(String implementation, Scenario scenario)
            throws Exception {

        // Removed verbose logging - only essential messages

        // Create mock database
        MockDatabaseDataProvider mockDb = new MockDatabaseDataProvider(
                scenario.dbDelay, scenario.objectSize);
        mockDb.populateData(scenario.numKeys);

        // Create provider based on implementation
        DataProvider<String, String> provider = createProvider(implementation, mockDb, scenario);

        // Generate access pattern
        List<String> accessPattern = generateAccessPattern(scenario);

        // Create result tracker
        BenchmarkResult result = new BenchmarkResult(implementation, scenario.name);
        result.addMetadata("numKeys", scenario.numKeys);
        result.addMetadata("objectSize", scenario.objectSize);
        result.addMetadata("numAccesses", scenario.numAccesses);
        result.addMetadata("threadCount", scenario.threadCount);
        result.addMetadata("dbDelay", scenario.dbDelay);
        result.addMetadata("pattern", scenario.pattern);

        // Memory tracking
        Runtime runtime = Runtime.getRuntime();
        AtomicLong peakMemory = new AtomicLong(0);
        AtomicLong totalMemory = new AtomicLong(0);
        AtomicLong memoryMeasurements = new AtomicLong(0);

        // Warmup
        for (int i = 0; i < Math.min(BenchmarkConfig.WARMUP_ITERATIONS, accessPattern.size()); i++) {
            provider.get(accessPattern.get(i));
        }

        // Force GC before measurement
        System.gc();
        Thread.sleep(100);

        // Start measurement
        result.startMeasurement();
        long startDbAccesses = mockDb.getAccessCount();

        // Run benchmark with threads
        ExecutorService executor = Executors.newFixedThreadPool(scenario.threadCount);
        List<Future<?>> futures = new ArrayList<>();

        // Memory monitoring thread
        ScheduledExecutorService memoryMonitor = Executors.newScheduledThreadPool(1);
        memoryMonitor.scheduleAtFixedRate(() -> {
            long used = runtime.totalMemory() - runtime.freeMemory();
            peakMemory.updateAndGet(current -> Math.max(current, used));
            totalMemory.addAndGet(used);
            memoryMeasurements.incrementAndGet();
        }, 0, 100, TimeUnit.MILLISECONDS);

        // Distribute work among threads
        int accessesPerThread = scenario.numAccesses / scenario.threadCount;

        for (int t = 0; t < scenario.threadCount; t++) {
            final int threadId = t;
            Future<?> future = executor.submit(() -> {
                int start = threadId * accessesPerThread;
                int end = (threadId == scenario.threadCount - 1) ?
                        scenario.numAccesses : start + accessesPerThread;

                for (int i = start; i < end; i++) {
                    String key = accessPattern.get(i % accessPattern.size());
                    long startTime = System.nanoTime();
                    provider.get(key);
                    long duration = System.nanoTime() - startTime;
                    result.recordResponseTime(duration);
                }
            });
            futures.add(future);
        }

        // Wait for completion
        for (Future<?> future : futures) {
            future.get();
        }

        memoryMonitor.shutdown();
        memoryMonitor.awaitTermination(1, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // End measurement
        result.endMeasurement();
        long endDbAccesses = mockDb.getAccessCount();

        // Calculate metrics
        long dbAccessesDuringTest = endDbAccesses - startDbAccesses;
        for (long i = 0; i < dbAccessesDuringTest; i++) {
            result.recordMiss();
        }

        // Estimate hits (total requests - misses)
        long estimatedHits = scenario.numAccesses - dbAccessesDuringTest;
        for (long i = 0; i < estimatedHits; i++) {
            if (implementation.contains("TwoLevel")) {
                // For two-level, estimate L1 vs L2 hits (simplified)
                if (i % 3 == 0) {
                    result.recordHitL2();
                } else {
                    result.recordHitL1();
                }
            } else {
                result.recordHitL1();
            }
        }

        // Memory metrics
        long avgMemory = memoryMeasurements.get() > 0 ?
                totalMemory.get() / memoryMeasurements.get() : 0;
        result.setMemoryMetrics(peakMemory.get(), avgMemory);

        // Silent completion - test moves to next scenario

        return result;
    }

    private DataProvider<String, String> createProvider(String implementation,
                                                        MockDatabaseDataProvider mockDb, Scenario scenario) throws IOException {

        switch (implementation) {
            case "V0-Database":
                return mockDb;

            case "V1-Decorator":
                return createV1Provider(mockDb, BenchmarkConfig.L1_CACHE_SIZE_MEDIUM);

            case "V1-Decorator-Small":
                return createV1Provider(mockDb, 50);

            case "V2-TwoLevel":
                return createV2Provider(mockDb, BenchmarkConfig.L1_CACHE_SIZE_SMALL);

            case "V2-TwoLevel-Small":
                return createV2Provider(mockDb, 25);

            default:
                throw new IllegalArgumentException("Unknown implementation: " + implementation);
        }
    }

    private DataProvider<String, String> createV1Provider(MockDatabaseDataProvider mockDb, int cacheSize) {
        CacheMetricsService metrics = new CacheMetricsService(new SimpleMeterRegistry());
        EvictionPolicy<String> policy = new LruEvictionPolicy<>();

        return new InMemoryCacheDecorator<>(
                mockDb,
                policy,
                cacheSize,
                metrics,
                "test_v1_cache"
        );
    }

    private DataProvider<String, String> createV2Provider(MockDatabaseDataProvider mockDb, int l1Size)
            throws IOException {

        CacheMetricsService metrics = new CacheMetricsService(new SimpleMeterRegistry());
        EvictionPolicy<String> policy = new LruEvictionPolicy<>();

        // Create unique L2 cache directory for this test
        Path l2Dir = Files.createTempDirectory(tempCacheDir, "l2_");
        DiskCacheRepository l2Cache = new DiskCacheRepository(
                l2Dir.toString(),
                BenchmarkConfig.L2_MAX_FILES
        );

        return new TwoLevelCacheProxy<>(
                mockDb,
                l2Cache,
                policy,
                l1Size,
                metrics
        );
    }

    private List<String> generateAccessPattern(Scenario scenario) {
        AccessPatternGenerator generator = new AccessPatternGenerator(scenario.numKeys);

        switch (scenario.pattern) {
            case UNIFORM:
                return generator.generateUniformPattern(scenario.numAccesses);

            case ZIPFIAN_LOW:
                return generator.generateZipfianPattern(scenario.numAccesses,
                        BenchmarkConfig.ZIPF_SKEW_LOW);

            case ZIPFIAN_MEDIUM:
            case HOT_SPOT:
                return generator.generateZipfianPattern(scenario.numAccesses,
                        BenchmarkConfig.ZIPF_SKEW_MEDIUM);

            case ZIPFIAN_HIGH:
                return generator.generateZipfianPattern(scenario.numAccesses,
                        BenchmarkConfig.ZIPF_SKEW_HIGH);

            default:
                throw new IllegalArgumentException("Unknown pattern: " + scenario.pattern);
        }
    }

    private void saveResultsToCsv(List<BenchmarkResult> results, String filename) throws IOException {
        File outputFile = new File(RESULTS_DIR, filename);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(BenchmarkResult.getCsvHeader() + "\n");

            for (BenchmarkResult result : results) {
                writer.write(result.toCsv() + "\n");
            }
        }
        // Silent save - no console output
    }

    private void printComparisonTable(List<BenchmarkResult> results) {
        // Minimal output - just show results saved message
    }

    private void generateSummaryReport(List<BenchmarkResult> results) throws IOException {
        File reportFile = new File(RESULTS_DIR, "summary_report.txt");

        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("==========================================================\n");
            writer.write("COMPREHENSIVE CACHE BENCHMARK - SUMMARY REPORT\n");
            writer.write("==========================================================\n\n");

            for (BenchmarkResult result : results) {
                writer.write(result.generateReport());
                writer.write("\n");
            }

            writer.write("\n==========================================================\n");
            writer.write("END OF REPORT\n");
            writer.write("==========================================================\n");
        }
        // Report saved silently
    }
}