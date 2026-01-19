package com.project.cacheEvict.test;

/**
 * Configuration class for benchmark tests.
 * Centralizes all configurable parameters mentioned in the presentation.
 *
 * NOTE: This is the FAST configuration optimized for 3-5 minute total runtime.
 * - Keys: 500/2k (small datasets for speed)
 * - Accesses: 500/1k/2k (fewer iterations)
 * - DB delays: 10ms/30ms/50ms (REALISTIC - ensures L2 cache shows value)
 * - Thread counts: 1/4 (reduced from 1/4/8/16)
 * - Warmup: 100 iterations (minimal)
 *
 * Key trade-off: We keep datasets SMALL but DB delays REALISTIC.
 * This ensures fast test execution while properly demonstrating cache benefits.
 */
public class BenchmarkConfig {

    // Object sizes (1KB and 100KB as per presentation)
    public static final int OBJECT_SIZE_SMALL = 1024;           // 1 KB
    public static final int OBJECT_SIZE_LARGE = 100 * 1024;     // 100 KB

    // Data volumes - ULTRA-FAST configuration
    public static final int KEYS_SMALL = 500;                   // 500 keys (reduced for speed)
    public static final int KEYS_LARGE = 2_000;                 // 2k keys (reduced for speed)

    // Thread counts for scalability testing - MINIMAL
    public static final int[] THREAD_COUNTS = {1, 4};           // Only 2 thread counts

    // Database delay configurations - REALISTIC delays for proper cache evaluation
    public static final long DB_DELAY_NONE = 0;                 // No delay
    public static final long DB_DELAY_FAST = 20;                // 10ms - fast DB
    public static final long DB_DELAY_MEDIUM = 50;              // 30ms - realistic DB
    public static final long DB_DELAY_SLOW = 80;                // 50ms - slow DB

    // Cache sizes
    public static final int L1_CACHE_SIZE_SMALL = 50;           // V2 default
    public static final int L1_CACHE_SIZE_MEDIUM = 100;         // V1 default
    public static final int L1_CACHE_SIZE_LARGE = 1000;         // Large cache

    public static final int L2_MAX_FILES = 100;                 // L2 disk cache limit (reduced)

    // Access pattern configurations - ULTRA-FAST
    public static final int NUM_ACCESSES_SMALL = 500;           // Very small workload
    public static final int NUM_ACCESSES_MEDIUM = 1_000;        // Small workload
    public static final int NUM_ACCESSES_LARGE = 2_000;         // Medium workload

    // Zipfian skew factors
    public static final double ZIPF_SKEW_LOW = 0.5;             // Low locality
    public static final double ZIPF_SKEW_MEDIUM = 1.0;          // Medium locality (80/20 rule)
    public static final double ZIPF_SKEW_HIGH = 1.5;            // High locality

    // Warmup parameters - MINIMAL
    public static final int WARMUP_ITERATIONS = 100;            // Very minimal warmup
    public static final int WARMUP_DURATION_MS = 200;           // Very short warmup

    // Measurement parameters
    public static final int MEASUREMENT_ITERATIONS = 5;

    /**
     * Test scenario configuration
     */
    public static class Scenario {
        public final String name;
        public final int numKeys;
        public final int objectSize;
        public final int numAccesses;
        public final int threadCount;
        public final long dbDelay;
        public final AccessPattern pattern;

        public Scenario(String name, int numKeys, int objectSize, int numAccesses,
                        int threadCount, long dbDelay, AccessPattern pattern) {
            this.name = name;
            this.numKeys = numKeys;
            this.objectSize = objectSize;
            this.numAccesses = numAccesses;
            this.threadCount = threadCount;
            this.dbDelay = dbDelay;
            this.pattern = pattern;
        }

        @Override
        public String toString() {
            return String.format("%s [keys=%d, size=%dB, accesses=%d, threads=%d, delay=%dms, pattern=%s]",
                    name, numKeys, objectSize, numAccesses, threadCount, dbDelay, pattern);
        }
    }

    public enum AccessPattern {
        UNIFORM,
        ZIPFIAN_LOW,
        ZIPFIAN_MEDIUM,
        ZIPFIAN_HIGH,
        HOT_SPOT
    }

    /**
     * Get all standard test scenarios as per presentation requirements
     */
    public static Scenario[] getStandardScenarios() {
        return new Scenario[] {
                // Small object, small dataset, different patterns
                new Scenario("Small-Uniform-1T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 1, DB_DELAY_MEDIUM, AccessPattern.UNIFORM),
                new Scenario("Small-Zipfian-1T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 1, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),

                // Large object, small dataset
                new Scenario("Large-Uniform-1T", KEYS_SMALL, OBJECT_SIZE_LARGE,
                        NUM_ACCESSES_SMALL, 1, DB_DELAY_MEDIUM, AccessPattern.UNIFORM),
                new Scenario("Large-Zipfian-1T", KEYS_SMALL, OBJECT_SIZE_LARGE,
                        NUM_ACCESSES_SMALL, 1, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),

                // Scalability tests - varying thread counts
                new Scenario("Scalability-1T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 1, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
                new Scenario("Scalability-4T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 4, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
                new Scenario("Scalability-8T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 8, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
                new Scenario("Scalability-16T", KEYS_SMALL, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_MEDIUM, 16, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),

                // Large dataset tests
                new Scenario("LargeDataset-Zipfian-1T", KEYS_LARGE, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_LARGE, 1, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
                new Scenario("LargeDataset-Zipfian-4T", KEYS_LARGE, OBJECT_SIZE_SMALL,
                        NUM_ACCESSES_LARGE, 4, DB_DELAY_MEDIUM, AccessPattern.ZIPFIAN_MEDIUM),
        };
    }
}