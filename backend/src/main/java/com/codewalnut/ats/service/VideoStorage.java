package com.codewalnut.ats.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Interview videos on disk (ADR-0030): a folder that, on Railway, is a mounted volume
 * (ATS_MEDIA_DIR=/data/media). Keys are made by the app, never taken from a request.
 */
@Slf4j
@Service
public class VideoStorage {

    private final Path root;

    public VideoStorage(@Value("${ats.media.dir:data/media}") String dir) {
        this.root = usable(Path.of(dir).toAbsolutePath().normalize());
    }

    /**
     * The configured folder if the app can write there; otherwise a folder in the temp directory,
     * so uploads still work (but don't survive a redeploy) and the log says what to fix.
     */
    static Path usable(Path wanted) {
        if (writable(wanted)) {
            log.info("Interview videos are saved in {}", wanted);
            return wanted;
        }
        Path fallback = Path.of(System.getProperty("java.io.tmpdir"), "ats-media").toAbsolutePath().normalize();
        log.error("Can't write interview videos to {} (ATS_MEDIA_DIR). Using {} instead, which is lost on every deploy. "
                + "On Railway: mount a volume and set ATS_MEDIA_DIR to a folder on it (see docs/deploy-railway.md).", wanted, fallback);
        return fallback;
    }

    private static boolean writable(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, "probe-", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Writes the stream to the key (replacing any file there) and returns its size in bytes. */
    public long save(String key, InputStream data) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "upload-", ".part");
            try {
                long size = Files.copy(data, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return size;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            log.error("Couldn't save interview video {}: {}", key, e.toString());
            throw new StorageUnavailableException();
        }
    }

    /** The file for streaming (Spring serves byte ranges, so the player can seek), or null if it's gone. */
    public Resource load(String key) {
        Path file = resolve(key);
        return Files.isRegularFile(file) ? new FileSystemResource(file) : null;
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Couldn't delete video {}: {}", key, e.getMessage());
        }
    }

    /** The server couldn't write the file (disk full, folder not writable); shown as 503 with a plain message. */
    public static class StorageUnavailableException extends RuntimeException {
        public StorageUnavailableException() {
            super("The server couldn't save your recording right now. Please try uploading again in a minute; "
                    + "if it keeps failing, message the CodeWalnut team.");
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Bad storage key");
        }
        return p;
    }
}
