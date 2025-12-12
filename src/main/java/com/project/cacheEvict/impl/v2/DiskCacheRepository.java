package com.project.cacheEvict.impl.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Gestionează nivelul 2 de cache (L2) - Persistent pe Disk.
 * Include acum o politică de evictare bazată pe Last-Modified-Time.
 */
@Component
public class DiskCacheRepository {

    private static final Logger logger = LoggerFactory.getLogger(DiskCacheRepository.class);
    private final Path cacheDirectory;
    private final int maxFiles; // Limita pentru L2

    // Injectăm calea și limita maximă de fișiere
    public DiskCacheRepository(
            @Value("${app.l2.cache-dir:./cache_l2_storage}") String cacheDirPath,
            @Value("${app.l2.max-files:200}") int maxFiles) { // Default 200 de fișiere

        this.cacheDirectory = Paths.get(cacheDirPath).toAbsolutePath().normalize();
        this.maxFiles = maxFiles;
        initializeCacheDirectory();
    }

    private void initializeCacheDirectory() {
        try {
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
                logger.info("V2: L2 Disk Cache directory created at: {}", cacheDirectory);
            } else {
                logger.info("V2: L2 Disk Cache directory found at: {}", cacheDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize L2 cache directory at " + cacheDirectory, e);
        }
    }

    public void save(String key, String value) {
        try {
            // 1. Verificăm dacă trebuie să facem loc (Eviction)
            enforceCapacity();

            // 2. Scriem noul fișier
            Path filePath = getFilePath(key);
            Files.writeString(filePath, value);
        } catch (IOException e) {
            logger.error("Failed to write to L2 cache for key: {}", key, e);
        }
    }

    public String load(String key) {
        try {
            Path filePath = getFilePath(key);
            if (Files.exists(filePath)) {
                // "TOUCH" - Actualizăm timpul modificării pentru a marca accesul (LRU logic)
                // Astfel, acest fișier devine "cel mai nou" și nu va fi șters curând.
                try {
                    Files.setLastModifiedTime(filePath, FileTime.from(Instant.now()));
                } catch (IOException ignored) {
                    // Ignorăm eroarea de touch, nu e critică pentru citire
                }

                return Files.readString(filePath);
            }
        } catch (IOException e) {
            logger.error("Failed to read from L2 cache for key: {}", key, e);
        }
        return null;
    }

    public void delete(String key) {
        try {
            Path filePath = getFilePath(key);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.error("Failed to delete from L2 cache for key: {}", key, e);
        }
    }

    /**
     * Verifică numărul de fișiere și șterge pe cel mai vechi accesat dacă limita e depășită.
     */
    private void enforceCapacity() {
        try (Stream<Path> stream = Files.list(cacheDirectory)) {
            // Numărăm fișierele (poate fi costisitor dacă sunt mii, dar ok pentru demo)
            long count = stream.filter(Files::isRegularFile).count();

            if (count >= maxFiles) {
                logger.info("V2: L2 Cache limit reached ({} files). Triggering eviction...", count);
                evictOldestFile();
            }
        } catch (IOException e) {
            logger.error("Error checking L2 capacity", e);
        }
    }

    private void evictOldestFile() {
        try (Stream<Path> stream = Files.list(cacheDirectory)) {
            Optional<Path> oldestFile = stream
                    .filter(Files::isRegularFile)
                    // Sortăm după LastModifiedTime (cel mai vechi primul)
                    .min(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return Long.MAX_VALUE; // Punem la sfârșit fișierele cu erori
                        }
                    }));

            if (oldestFile.isPresent()) {
                Path toDelete = oldestFile.get();
                logger.info("V2: Evicting L2 file: {}", toDelete.getFileName());
                Files.delete(toDelete);
            }
        } catch (IOException e) {
            logger.error("Error during L2 eviction", e);
        }
    }

    public void clearAll() {
        try {
            if (Files.exists(cacheDirectory)) {
                try (Stream<Path> walk = Files.walk(cacheDirectory)) {
                    walk.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(cacheDirectory))
                            .forEach(path -> {
                                try { Files.delete(path); } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException e) {
            logger.error("Failed to clear L2 cache", e);
        }
    }

    private Path getFilePath(String key) {
        String safeFileName = key.replaceAll("[^a-zA-Z0-9.-]", "_") + ".cache";
        return cacheDirectory.resolve(safeFileName);
    }
}