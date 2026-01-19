package com.project.cacheEvict.test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Collects and analyzes benchmark results for cache implementations.
 */
public class BenchmarkResult {
    
    private final String implementation;
    private final String scenario;
    private final Map<String, Object> metadata;
    
    // Performance metrics
    private final List<Long> responseTimes;
    private final AtomicLong totalHitsL1;
    private final AtomicLong totalHitsL2;
    private final AtomicLong totalMisses;
    private final AtomicLong totalRequests;
    private final AtomicLong dbAccesses;
    
    // Resource metrics
    private long peakMemoryUsageBytes;
    private long averageMemoryUsageBytes;
    private double cpuUtilization;
    
    // Timing
    private long startTimeMs;
    private long endTimeMs;
    
    public BenchmarkResult(String implementation, String scenario) {
        this.implementation = implementation;
        this.scenario = scenario;
        this.metadata = new ConcurrentHashMap<>();
        this.responseTimes = Collections.synchronizedList(new ArrayList<>());
        this.totalHitsL1 = new AtomicLong(0);
        this.totalHitsL2 = new AtomicLong(0);
        this.totalMisses = new AtomicLong(0);
        this.totalRequests = new AtomicLong(0);
        this.dbAccesses = new AtomicLong(0);
    }
    
    public void startMeasurement() {
        this.startTimeMs = System.currentTimeMillis();
    }
    
    public void endMeasurement() {
        this.endTimeMs = System.currentTimeMillis();
    }
    
    public void recordResponseTime(long nanos) {
        responseTimes.add(nanos);
    }
    
    public void recordHitL1() {
        totalHitsL1.incrementAndGet();
        totalRequests.incrementAndGet();
    }
    
    public void recordHitL2() {
        totalHitsL2.incrementAndGet();
        totalRequests.incrementAndGet();
    }
    
    public void recordMiss() {
        totalMisses.incrementAndGet();
        totalRequests.incrementAndGet();
        dbAccesses.incrementAndGet();
    }
    
    public void recordDbAccess() {
        dbAccesses.incrementAndGet();
    }
    
    public void setMemoryMetrics(long peakBytes, long averageBytes) {
        this.peakMemoryUsageBytes = peakBytes;
        this.averageMemoryUsageBytes = averageBytes;
    }
    
    public void setCpuUtilization(double utilization) {
        this.cpuUtilization = utilization;
    }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    // Calculated metrics
    
    public double getHitRatio() {
        long requests = totalRequests.get();
        if (requests == 0) return 0.0;
        long hits = totalHitsL1.get() + totalHitsL2.get();
        return (double) hits / requests;
    }
    
    public double getL1HitRatio() {
        long requests = totalRequests.get();
        if (requests == 0) return 0.0;
        return (double) totalHitsL1.get() / requests;
    }
    
    public double getL2HitRatio() {
        long requests = totalRequests.get();
        if (requests == 0) return 0.0;
        return (double) totalHitsL2.get() / requests;
    }
    
    public double getMissRatio() {
        return 1.0 - getHitRatio();
    }
    
    public double getThroughput() {
        long durationMs = endTimeMs - startTimeMs;
        if (durationMs == 0) return 0.0;
        return (totalRequests.get() * 1000.0) / durationMs;
    }
    
    public long getAverageLatencyNanos() {
        if (responseTimes.isEmpty()) return 0;
        return (long) responseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }
    
    public long getMedianLatencyNanos() {
        if (responseTimes.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(responseTimes);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
    
    public long getP95LatencyNanos() {
        return getPercentileLatency(95);
    }
    
    public long getP99LatencyNanos() {
        return getPercentileLatency(99);
    }
    
    public long getMinLatencyNanos() {
        if (responseTimes.isEmpty()) return 0;
        return responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
    }
    
    public long getMaxLatencyNanos() {
        if (responseTimes.isEmpty()) return 0;
        return responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
    }
    
    private long getPercentileLatency(int percentile) {
        if (responseTimes.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(responseTimes);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
    
    public long getTotalDurationMs() {
        return endTimeMs - startTimeMs;
    }
    
    // Getters
    
    public String getImplementation() {
        return implementation;
    }
    
    public String getScenario() {
        return scenario;
    }
    
    public long getTotalRequests() {
        return totalRequests.get();
    }
    
    public long getTotalHitsL1() {
        return totalHitsL1.get();
    }
    
    public long getTotalHitsL2() {
        return totalHitsL2.get();
    }
    
    public long getTotalMisses() {
        return totalMisses.get();
    }
    
    public long getDbAccesses() {
        return dbAccesses.get();
    }
    
    public long getPeakMemoryUsageBytes() {
        return peakMemoryUsageBytes;
    }
    
    public long getAverageMemoryUsageBytes() {
        return averageMemoryUsageBytes;
    }
    
    public double getCpuUtilization() {
        return cpuUtilization;
    }
    
    /**
     * Generate a formatted report
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================\n");
        sb.append(String.format("Implementation: %s\n", implementation));
        sb.append(String.format("Scenario: %s\n", scenario));
        sb.append("========================================\n\n");
        
        sb.append("PERFORMANCE METRICS:\n");
        sb.append(String.format("  Total Requests: %,d\n", getTotalRequests()));
        sb.append(String.format("  Total Duration: %,d ms\n", getTotalDurationMs()));
        sb.append(String.format("  Throughput: %.2f req/sec\n", getThroughput()));
        sb.append("\n");
        
        sb.append("HIT RATIOS:\n");
        sb.append(String.format("  Overall Hit Ratio: %.2f%%\n", getHitRatio() * 100));
        sb.append(String.format("  L1 Hit Ratio: %.2f%%\n", getL1HitRatio() * 100));
        sb.append(String.format("  L2 Hit Ratio: %.2f%%\n", getL2HitRatio() * 100));
        sb.append(String.format("  Miss Ratio: %.2f%%\n", getMissRatio() * 100));
        sb.append("\n");
        
        sb.append("LATENCY (microseconds):\n");
        sb.append(String.format("  Min: %.2f µs\n", getMinLatencyNanos() / 1000.0));
        sb.append(String.format("  Average: %.2f µs\n", getAverageLatencyNanos() / 1000.0));
        sb.append(String.format("  Median: %.2f µs\n", getMedianLatencyNanos() / 1000.0));
        sb.append(String.format("  P95: %.2f µs\n", getP95LatencyNanos() / 1000.0));
        sb.append(String.format("  P99: %.2f µs\n", getP99LatencyNanos() / 1000.0));
        sb.append(String.format("  Max: %.2f µs\n", getMaxLatencyNanos() / 1000.0));
        sb.append("\n");
        
        sb.append("RESOURCE USAGE:\n");
        sb.append(String.format("  Peak Memory: %.2f MB\n", peakMemoryUsageBytes / (1024.0 * 1024.0)));
        sb.append(String.format("  Avg Memory: %.2f MB\n", averageMemoryUsageBytes / (1024.0 * 1024.0)));
        sb.append(String.format("  CPU Utilization: %.2f%%\n", cpuUtilization));
        sb.append("\n");
        
        sb.append("DATABASE ACCESS:\n");
        sb.append(String.format("  Total DB Accesses: %,d\n", getDbAccesses()));
        sb.append(String.format("  DB Access Ratio: %.2f%%\n", 
                (getDbAccesses() * 100.0) / getTotalRequests()));
        sb.append("\n");
        
        if (!metadata.isEmpty()) {
            sb.append("METADATA:\n");
            metadata.forEach((k, v) -> sb.append(String.format("  %s: %s\n", k, v)));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Export to CSV format
     */
    public String toCsv() {
        return String.format("%s,%s,%d,%d,%.2f,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%.2f,%.2f",
                implementation,
                scenario,
                getTotalRequests(),
                getTotalDurationMs(),
                getThroughput(),
                getHitRatio(),
                getL1HitRatio(),
                getL2HitRatio(),
                getAverageLatencyNanos() / 1000.0,
                getMedianLatencyNanos() / 1000.0,
                getP95LatencyNanos() / 1000.0,
                getP99LatencyNanos() / 1000.0,
                getMaxLatencyNanos() / 1000.0,
                peakMemoryUsageBytes / (1024 * 1024),
                averageMemoryUsageBytes / (1024 * 1024),
                cpuUtilization,
                (getDbAccesses() * 100.0) / getTotalRequests()
        );
    }
    
    public static String getCsvHeader() {
        return "Implementation,Scenario,TotalRequests,DurationMs,ThroughputReqSec," +
               "HitRatio,L1HitRatio,L2HitRatio,AvgLatencyUs,MedianLatencyUs," +
               "P95LatencyUs,P99LatencyUs,MaxLatencyUs,PeakMemoryMB,AvgMemoryMB," +
               "CpuUtilization,DbAccessRatio";
    }
}
