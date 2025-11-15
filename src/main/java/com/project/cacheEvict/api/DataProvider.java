package com.project.cacheEvict.api;

public interface DataProvider<K, V> {
    V get(K key);
}
