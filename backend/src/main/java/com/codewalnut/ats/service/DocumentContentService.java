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

    /** Called in the same transaction as the document/intake insert: durable staging plus outbox. */
    public void stage(Target type, UUID id, byte[] data) {
        if (properties.enabled()) repository.enqueue(type, id, data, sha256(data));
    }

    public byte[] read(CandidateDocument document) {
        return read(Target.DOCUMENT, document.getId(), document.getData());
    }

    public byte[] read(ResumeIntake intake) {
        return read(Target.INTAKE, intake.getId(), intake.getData());
    }

    private byte[] read(Target type, UUID id, byte[] legacy) {
        var task = repository.find(type, id);
        if (task.isPresent() && "READY".equals(task.get().status()) && properties.enabled()) {
            try {
                byte[] bytes = store.get(task.get().storageKey());
                verify(task.get(), bytes);
                return bytes;
            } catch (DocumentStorageUnavailableException e) {
                // Only a verified retained copy is a valid fallback. Never fetch a public URL.
                if (legacy == null) throw e;
                verify(task.get(), legacy);
                return legacy;
            }
        }
        if (legacy != null) return legacy;
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
