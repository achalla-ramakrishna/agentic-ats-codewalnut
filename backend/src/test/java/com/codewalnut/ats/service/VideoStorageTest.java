package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Videos are saved where configured, or somewhere writable rather than failing every upload (AINT-04). */
class VideoStorageTest {

    @TempDir
    Path dir;

    @Test
    void savesLoadsAndReplaces() {
        VideoStorage storage = new VideoStorage(dir.resolve("media").toString());
        assertThat(storage.save("interviews/a/b.webm", new ByteArrayInputStream(new byte[] {1, 2, 3}))).isEqualTo(3);
        assertThat(storage.load("interviews/a/b.webm")).isNotNull();
        storage.delete("interviews/a/b.webm");
        assertThat(storage.load("interviews/a/b.webm")).isNull();
        assertThatThrownBy(() -> storage.load("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fallsBackWhenTheFolderIsNotWritable() throws Exception {
        Path locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            Path chosen = VideoStorage.usable(locked.resolve("media"));
            if (!System.getProperty("user.name").equals("root")) { // root can write anywhere
                assertThat(chosen).isNotEqualTo(locked.resolve("media"));
            }
            assertThat(Files.isWritable(chosen)).isTrue();
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
