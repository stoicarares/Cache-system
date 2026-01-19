package com.project.cacheEvict.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates key access patterns for cache testing.
 * Supports both Uniform and Zipfian distributions.
 */
public class AccessPatternGenerator {
    
    private final Random random;
    private final int totalKeys;
    
    public AccessPatternGenerator(int totalKeys, long seed) {
        this.totalKeys = totalKeys;
        this.random = new Random(seed);
    }
    
    public AccessPatternGenerator(int totalKeys) {
        this(totalKeys, System.currentTimeMillis());
    }
    
    /**
     * Generate uniform distribution - all keys have equal probability
     */
    public List<String> generateUniformPattern(int numAccesses) {
        List<String> accessPattern = new ArrayList<>(numAccesses);
        
        for (int i = 0; i < numAccesses; i++) {
            int keyIndex = random.nextInt(totalKeys);
            accessPattern.add("key_" + keyIndex);
        }
        
        return accessPattern;
    }
    
    /**
     * Generate Zipfian distribution - follows power law (locality of reference)
     * Small percentage of keys get majority of accesses
     * 
     * @param numAccesses Number of key accesses to generate
     * @param skewFactor Controls the skew (0.5-2.0 typical, higher = more skewed)
     */
    public List<String> generateZipfianPattern(int numAccesses, double skewFactor) {
        List<String> accessPattern = new ArrayList<>(numAccesses);
        ZipfianGenerator zipfian = new ZipfianGenerator(totalKeys, skewFactor);
        
        for (int i = 0; i < numAccesses; i++) {
            int keyIndex = zipfian.nextInt();
            accessPattern.add("key_" + keyIndex);
        }
        
        return accessPattern;
    }
    
    /**
     * Generate hot-spot pattern: 20% of keys receive 80% of traffic
     */
    public List<String> generateHotSpotPattern(int numAccesses) {
        return generateZipfianPattern(numAccesses, 1.0);
    }
    
    /**
     * Inner class implementing Zipfian distribution
     */
    private class ZipfianGenerator {
        private final int itemCount;
        private final double skew;
        private final double[] probabilities;
        
        public ZipfianGenerator(int itemCount, double skew) {
            this.itemCount = itemCount;
            this.skew = skew;
            this.probabilities = new double[itemCount];
            
            // Calculate normalization constant
            double sum = 0.0;
            for (int i = 1; i <= itemCount; i++) {
                sum += 1.0 / Math.pow(i, skew);
            }
            
            // Calculate cumulative probabilities
            double cumulativeProbability = 0.0;
            for (int i = 0; i < itemCount; i++) {
                double probability = (1.0 / Math.pow(i + 1, skew)) / sum;
                cumulativeProbability += probability;
                probabilities[i] = cumulativeProbability;
            }
        }
        
        public int nextInt() {
            double randomValue = random.nextDouble();
            
            // Binary search for the item
            int low = 0;
            int high = itemCount - 1;
            
            while (low < high) {
                int mid = (low + high) / 2;
                if (probabilities[mid] >= randomValue) {
                    high = mid;
                } else {
                    low = mid + 1;
                }
            }
            
            return low;
        }
    }
}
