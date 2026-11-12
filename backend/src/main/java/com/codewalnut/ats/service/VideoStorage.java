package com.codewalnut.ats.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
        this.root = Path.of(dir).toAbsolutePath().normalize();
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
            throw new UncheckedIOException("Couldn't save the video", e);
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

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Bad storage key");
        }
        return p;
    }
}
