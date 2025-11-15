package com.project.cacheEvict.web.dto;

public record CacheResponseDTO(
        String key,
        String data,
        String sourceImplementation,
        long executionTimeMs
) {}