package com.codewalnut.ats.service;

import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.config.DocumentStorageProperties;
import com.codewalnut.ats.domain.BackgroundTask;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.ResumeIntake;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Internal content access. Callers must authorize the document before calling this service. */
@Service
@RequiredArgsConstructor
public class DocumentContentService {
    private final DocumentStorageRepository repository;
    private final DocumentStorageProperties properties;
    private final PrivateDocumentStore store;

    /** Called in the same transaction as the document insert: durable staging plus outbox. */
    public void stage(Target type, UUID id, byte[] data) {
        if (properties.enabled() && type == Target.DOCUMENT) repository.enqueue(type, id, data, sha256(data));
    }

    /** Immutable storage coordinates captured while the caller's metadata transaction is open. */
    public record Source(UUID documentId, String fileName, String contentType, BackgroundTask manifest, byte[] legacy) {}

    public Source snapshot(CandidateDocument document) {
        return new Source(document.getId(), document.getFileName(), document.getContentType(),
                repository.find(Target.DOCUMENT, document.getId()).orElse(null), document.getData());
    }

    public byte[] read(ResumeIntake intake) {
        // Temporary uploads keep their existing delete-after-processing lifecycle.
        if (intake.getData() == null) throw new DocumentStorageUnavailableException();
        return intake.getData();
    }

    /** Resolve the captured source only after the caller has finished its database transaction. */
    public byte[] read(Source source) {
        BackgroundTask task = source.manifest();
        byte[] legacy = source.legacy();
        if (legacy != null) {
            if (task != null && "READY".equals(task.status())) verify(task, legacy);
            return legacy;
        }
        if (task != null && "READY".equals(task.status()) && properties.enabled()) {
            byte[] bytes = store.get(task.storageKey());
            verify(task, bytes);
            return bytes;
        }
        throw new DocumentStorageUnavailableException();
    }

    public static void verify(BackgroundTask task, byte[] bytes) {
        if (bytes == null || bytes.length != task.sizeBytes() || !sha256(bytes).equals(task.sha256())) {
            throw new DocumentStorageUnavailableException();
        }
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
