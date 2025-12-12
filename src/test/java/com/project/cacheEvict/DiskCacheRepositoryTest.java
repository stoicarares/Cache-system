package com.project.cacheEvict;

import com.project.cacheEvict.impl.db.ProductData;
import com.project.cacheEvict.impl.db.ProductRepository;
import com.project.cacheEvict.impl.v2.DiskCacheRepository;
import com.project.cacheEvict.web.dto.CacheResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.HttpStatus.OK;

public class DiskCacheRepositoryTest {

    // @TempDir este specific JUnit 5. Dacă folosești JUnit 4, acesta va fi null.
    @TempDir
    Path tempDir;

    private DiskCacheRepository repository;

    // @BeforeEach este specific JUnit 5. JUnit 4 ar ignora asta (căutând @Before).
    @BeforeEach
    void setUp() {
        // Dacă tempDir e null, înseamnă că rulezi cu JUnit 4 sau ai importuri greșite.
        if (tempDir == null) {
            throw new IllegalStateException("@TempDir is null. Are you running with JUnit 5?");
        }

        // Inițializăm repository-ul cu folderul temporar
        // Setăm limita la 2 fișiere pentru teste
        repository = new DiskCacheRepository(tempDir.toString(), 2);
    }

    @Test
    public void testSaveAndLoad() {
        String key = "testKey";
        String value = "testValue";

        repository.save(key, value);

        String loadedValue = repository.load(key);
        assertEquals(value, loadedValue);

        assertTrue(Files.exists(tempDir.resolve("testKey.cache")));
    }

    @Test
    public void testEviction_ShouldRemoveOldestFile_WhenCapacityExceeded() throws InterruptedException, IOException {
        repository.save("fileA", "contentA");
        // Pauză mică pentru a asigura diferențe de timestamp
        Thread.sleep(50);

        repository.save("fileB", "contentB");
        Thread.sleep(50);

        assertEquals(2, Files.list(tempDir).count());

        // Aici limita este depășită (2). Ar trebui să șteargă 'fileA' (cel mai vechi).
        repository.save("fileC", "contentC");

        assertEquals(2, Files.list(tempDir).count());

        assertNull(repository.load("fileA"), "FileA trebuia șters");
        assertNotNull(repository.load("fileB"));
        assertNotNull(repository.load("fileC"));
    }

    @Test
    public void testLruLogic_AccessShouldPreventEviction() throws InterruptedException, IOException {
        repository.save("fileA", "contentA");
        Thread.sleep(50);

        repository.save("fileB", "contentB");
        Thread.sleep(50);

        // Accesăm A -> devine "cel mai recent"
        repository.load("fileA");
        Thread.sleep(50);

        // Adăugăm C -> B este acum cel mai vechi nefolosit, deci B zboară.
        repository.save("fileC", "contentC");

        assertEquals(2, Files.list(tempDir).count());

        assertNotNull(repository.load("fileA"), "FileA trebuia păstrat (accesat recent)");
        assertNull(repository.load("fileB"), "FileB trebuia șters");
        assertNotNull(repository.load("fileC"));
    }

    @Test
    public void testClearAll() throws IOException {
        repository.save("k1", "v1");
        repository.save("k2", "v2");

        assertEquals(2, Files.list(tempDir).count());

        repository.clearAll();

        assertEquals(0, Files.list(tempDir).count());
    }
}