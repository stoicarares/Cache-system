package com.project.cacheEvict.impl.v0;

import com.project.cacheEvict.api.DataProvider;
import com.project.cacheEvict.impl.db.ProductData;
import com.project.cacheEvict.impl.db.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service("v0_Database")
public class DatabaseDataProvider implements DataProvider<String, String> {

    private final ProductRepository productRepository;
    private final long delayMs;

    public DatabaseDataProvider(ProductRepository productRepository,
                                @Value("${app.db.delay-ms:0}") long delayMs) {
        this.productRepository = productRepository;
        this.delayMs = delayMs;
        System.out.println("V0: Initialized REAL DatabaseDataProvider with " + delayMs + "ms delay.");
    }

    @Override
    public String get(String key) {
        Optional<ProductData> result = productRepository.findById(key);

        simulateSlowOperation();

        return result.map(ProductData::getProductValue)
                .orElse(null);
    }

    private void simulateSlowOperation() {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}