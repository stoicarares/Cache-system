package com.project.cacheEvict.api;

public interface EvictionPolicy<K> {
    void keyAccessed(K key);
    void keyRemoved(K key);
    K getKeyToEvict();
}