package com.project.cacheEvict;

import com.project.cacheEvict.impl.db.ProductData;
import com.project.cacheEvict.impl.db.ProductRepository;
import com.project.cacheEvict.web.dto.CacheResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CacheV1IntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ProductRepository productRepository;

	@BeforeEach
	void setUp() {
		productRepository.deleteAll();
		productRepository.save(new ProductData("int-key-1", "Integration Test Data"));
	}

	@Test
	void testCacheV1_DemonstratesMissAndHit() {
		String testKey = "int-key-1";
		String url = "/api/demo/v1/" + testKey;

		long startTimeMiss = System.nanoTime();
		ResponseEntity<CacheResponseDTO> responseMiss = restTemplate.getForEntity(url, CacheResponseDTO.class);
		long durationMiss = (System.nanoTime() - startTimeMiss) / 1_000_000;

		assertEquals(OK, responseMiss.getStatusCode());
		assertNotNull(responseMiss.getBody());
		assertEquals("Integration Test Data", responseMiss.getBody().data());
		assertTrue(durationMiss > 450, "Primul apel (MISS) ar trebui să fie lent. Durata: " + durationMiss + "ms");


		long startTimeHit = System.nanoTime();
		ResponseEntity<CacheResponseDTO> responseHit = restTemplate.getForEntity(url, CacheResponseDTO.class);
		long durationHit = (System.nanoTime() - startTimeHit) / 1_000_000;

		assertEquals(OK, responseHit.getStatusCode());
		assertNotNull(responseHit.getBody());
		assertEquals("Integration Test Data", responseHit.getBody().data());
		assertTrue(durationHit < 50, "Al doilea apel (HIT) ar trebui să fie rapid. Durata: " + durationHit + "ms");
	}
}