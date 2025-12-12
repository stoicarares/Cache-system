package com.project.cacheEvict.web;

import com.project.cacheEvict.impl.v2.TwoLevelCacheProxy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.web.dto.CacheResponseDTO;

@RestController
@RequestMapping("/api/demo")
//@CrossOrigin(origins = "http://localhost:4200")
public class CacheDemoController {

    private final DataProvider<String, String> v0Provider;
    private final DataProvider<String, String> v1Provider;
    private final DataProvider<String, String> v2Provider;

    public CacheDemoController(
            @Qualifier("v0_Database") DataProvider<String, String> v0Provider,
            @Qualifier("v1_Cache") DataProvider<String, String> v1Provider,
            @Qualifier("v2_Cache") DataProvider<String, String> v2Provider){
        this.v0Provider = v0Provider;
        this.v1Provider = v1Provider;
        this.v2Provider = v2Provider;
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

    // --- Endpoint SPRINT 2 ---
    @GetMapping("/v2/{key}")
    public CacheResponseDTO getV2Data(@PathVariable String key) {
        return measureExecution(v2Provider, key, "v2_Proxy_L1_L2");
    }

    @DeleteMapping("/v2/cache/l1")
    public ResponseEntity<String> evictL1Cache() {
        if (v2Provider instanceof TwoLevelCacheProxy<String, String>) {
            ((TwoLevelCacheProxy<String, String>) v2Provider).clearL1();
            return ResponseEntity.ok("V2: L1 Cache (Memory) a fost golit cu succes.");
        }
        return ResponseEntity.badRequest().body("Implementarea V2 nu suportă ștergerea L1.");
    }

    @DeleteMapping("/v2/cache/l2")
    public ResponseEntity<String> evictL2Cache() {
        if (v2Provider instanceof TwoLevelCacheProxy) {
            ((TwoLevelCacheProxy<String, String>) v2Provider).clearL2();
            return ResponseEntity.ok("V2: L2 Cache (Disk) a fost golit cu succes.");
        }
        return ResponseEntity.badRequest().body("Implementarea V2 nu suportă ștergerea L2.");
    }

    // --- Helper ---

    private CacheResponseDTO measureExecution(DataProvider<String, String> provider, String key, String sourceName) {
        long startTime = System.nanoTime();
        String data = provider.get(key);
        long durationMs = (System.nanoTime() - startTime);
        return new CacheResponseDTO(key, data, sourceName, durationMs);
    }
}