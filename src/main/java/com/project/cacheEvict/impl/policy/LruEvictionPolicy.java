package com.project.cacheEvict.impl.policy;

import com.project.cacheEvict.api.EvictionPolicy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class LruEvictionPolicy<K> implements EvictionPolicy<K> {
    private final Deque<K> accessOrder = new ArrayDeque<>();
    private final Set<K> keySet = new HashSet<>();

    @Override
    public void keyAccessed(K key) {
        if (keySet.contains(key)) {
            accessOrder.remove(key);
        }
        accessOrder.addLast(key);
        keySet.add(key);
    }

    @Override
    public void keyRemoved(K key) {
        if (keySet.contains(key)) {
            accessOrder.remove(key);
            keySet.remove(key);
        }
    }

    @Override
    public K getKeyToEvict() {
        if (accessOrder.isEmpty()) {
            return null;
        }
        K evictedKey = accessOrder.removeFirst();
        keySet.remove(evictedKey);
        return evictedKey;
    }
}