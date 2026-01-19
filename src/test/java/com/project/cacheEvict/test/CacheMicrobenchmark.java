package com.project.cacheEvict.test;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.api.EvictionPolicy;
import com.project.cacheEvict.impl.policy.LruEvictionPolicy;
import com.project.cacheEvict.impl.v1.InMemoryCacheDecorator;
import com.project.cacheEvict.impl.v2.DiskCacheRepository;
import com.project.cacheEvict.impl.v2.TwoLevelCacheProxy;
import com.project.cacheEvict.metrics.CacheMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmarks for precise cache performance measurement.
 * 
 * Run with: mvn clean install && java -jar target/benchmarks.jar
 * Or from IDE: Run the main() method
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
public class CacheMicrobenchmark {
    
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        
        @Param({"1000", "10000"})
        public int numKeys;
        
        @Param({"1024", "102400"}) // 1KB, 100KB
        public int objectSize;
        
        @Param({"10", "50"}) // DB delay in ms
        public long dbDelay;
        
        // Providers
        public DataProvider<String, String> v0Provider;
        public DataProvider<String, String> v1Provider;
        public DataProvider<String, String> v2Provider;
        
        // Access patterns
        public List<String> uniformPattern;
        public List<String> zipfianPattern;
        
        public int accessIndex = 0;
        
        private Path tempCacheDir;
        
        @Setup(Level.Trial)
        public void setup() throws IOException {
            System.out.println("Setting up benchmark with " + numKeys + " keys, " + 
                              objectSize + "B objects, " + dbDelay + "ms delay");
            
            // Create temporary cache directory
            tempCacheDir = Files.createTempDirectory("jmh_l2_cache");
            
            // Setup V0 (Database only)
            MockDatabaseDataProvider mockDb0 = new MockDatabaseDataProvider(dbDelay, objectSize);
            mockDb0.populateData(numKeys);
            v0Provider = mockDb0;
            
            // Setup V1 (Decorator)
            MockDatabaseDataProvider mockDb1 = new MockDatabaseDataProvider(dbDelay, objectSize);
            mockDb1.populateData(numKeys);
            CacheMetricsService metrics1 = new CacheMetricsService(new SimpleMeterRegistry());
            EvictionPolicy<String> policy1 = new LruEvictionPolicy<>();
            v1Provider = new InMemoryCacheDecorator<>(
                    mockDb1, policy1, 100, metrics1, "jmh_v1");
            
            // Setup V2 (Two-Level)
            MockDatabaseDataProvider mockDb2 = new MockDatabaseDataProvider(dbDelay, objectSize);
            mockDb2.populateData(numKeys);
            CacheMetricsService metrics2 = new CacheMetricsService(new SimpleMeterRegistry());
            EvictionPolicy<String> policy2 = new LruEvictionPolicy<>();
            DiskCacheRepository l2Cache = new DiskCacheRepository(tempCacheDir.toString(), 200);
            v2Provider = new TwoLevelCacheProxy<>(
                    mockDb2, l2Cache, policy2, 50, metrics2);
            
            // Generate access patterns
            AccessPatternGenerator generator = new AccessPatternGenerator(numKeys);
            uniformPattern = generator.generateUniformPattern(10000);
            zipfianPattern = generator.generateZipfianPattern(10000, 1.0);
            
            System.out.println("Benchmark setup complete");
        }
        
        @TearDown(Level.Trial)
        public void teardown() throws IOException {
            // Cleanup L2 cache directory
            if (tempCacheDir != null && Files.exists(tempCacheDir)) {
                Files.walk(tempCacheDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try { Files.delete(path); } catch (IOException e) {}
                        });
            }
        }
        
        public String getNextKey(List<String> pattern) {
            String key = pattern.get(accessIndex % pattern.size());
            accessIndex++;
            return key;
        }
    }
    
    // ==================== V0 Benchmarks ====================
    
    @Benchmark
    public void v0_UniformAccess(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.uniformPattern);
        String value = state.v0Provider.get(key);
        blackhole.consume(value);
    }
    
    @Benchmark
    public void v0_ZipfianAccess(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v0Provider.get(key);
        blackhole.consume(value);
    }
    
    // ==================== V1 Benchmarks ====================
    
    @Benchmark
    public void v1_UniformAccess(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.uniformPattern);
        String value = state.v1Provider.get(key);
        blackhole.consume(value);
    }
    
    @Benchmark
    public void v1_ZipfianAccess(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v1Provider.get(key);
        blackhole.consume(value);
    }
    
    // ==================== V2 Benchmarks ====================
    
    @Benchmark
    public void v2_UniformAccess(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.uniformPattern);
        String value = state.v2Provider.get(key);
        blackhole.consume(value);
    }
    
    @Benchmark
    public void v2_ZipfianAccess(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v2Provider.get(key);
        blackhole.consume(value);
    }
    
    // ==================== Contention Benchmarks ====================
    
    @Benchmark
    @Threads(4)
    public void v1_ConcurrentAccess_4Threads(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v1Provider.get(key);
        blackhole.consume(value);
    }
    
    @Benchmark
    @Threads(8)
    public void v1_ConcurrentAccess_8Threads(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v1Provider.get(key);
        blackhole.consume(value);
    }
    
    @Benchmark
    @Threads(4)
    public void v2_ConcurrentAccess_4Threads(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v2Provider.get(key);
        blackhole.consume(value);
    }
    
    @Benchmark
    @Threads(8)
    public void v2_ConcurrentAccess_8Threads(BenchmarkState state, Blackhole blackhole) {
        String key = state.getNextKey(state.zipfianPattern);
        String value = state.v2Provider.get(key);
        blackhole.consume(value);
    }
    
    /**
     * Main method to run JMH benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(CacheMicrobenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .shouldFailOnError(true)
                .build();
        
        new Runner(opt).run();
    }
}
