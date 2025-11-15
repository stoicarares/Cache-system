package com.project.cacheEvict.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.web.dto.CacheResponseDTO;

@RestController
@RequestMapping("/api/demo")
//@CrossOrigin(origins = "http://localhost:4200")
public class CacheDemoController {

    private final DataProvider<String, String> v0Provider;
    private final DataProvider<String, String> v1Provider;

    public CacheDemoController(
            @Qualifier("v0_Database") DataProvider<String, String> v0Provider,
            @Qualifier("v1_Cache") DataProvider<String, String> v1Provider) {
        this.v0Provider = v0Provider;
        this.v1Provider = v1Provider;
    }

    @GetMapping("/v0/{key}")
    public CacheResponseDTO getV0Data(@PathVariable String key) {
        long startTime = System.nanoTime();
        String data = v0Provider.get(key);
        long durationMs = (System.nanoTime() - startTime);

        return new CacheResponseDTO(key, data, "v0_Database", durationMs);
    }

    @GetMapping("/v1/{key}")
    public CacheResponseDTO getV1Data(@PathVariable String key) {
        long startTime = System.nanoTime();
        String data = v1Provider.get(key);
        long durationMs = (System.nanoTime() - startTime);

        return new CacheResponseDTO(key, data, "v1_Cache", durationMs);
    }
}