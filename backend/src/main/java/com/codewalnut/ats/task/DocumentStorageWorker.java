package com.codewalnut.ats.task;

import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.config.DocumentStorageProperties;
import com.codewalnut.ats.domain.BackgroundTask;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import com.codewalnut.ats.service.DocumentContentService;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Serial, leased durable transfers. No provider call or file content is logged. */
@Slf4j
@Component
public class DocumentStorageWorker {
    private final DocumentStorageRepository repository;
    private final PrivateDocumentStore store;
    private final boolean enabled;

    public DocumentStorageWorker(DocumentStorageRepository repository, PrivateDocumentStore store, DocumentStorageProperties properties,
            @Value("${ats.background-work.enabled:true}") boolean background,
            @Value("${ats.operations.maintenance:false}") boolean maintenance,
            @Value("${ats.operations.rehearsal:false}") boolean rehearsal) {
        this.repository = repository;
        this.store = store;
        this.enabled = properties.enabled() && background && !maintenance && !rehearsal;
    }

    @Scheduled(scheduler = "documentStorageScheduler", fixedDelayString = "${ats.document-storage.poll-ms:30000}", initialDelayString = "${ats.document-storage.poll-ms:30000}")
    public void poll() {
        if (!enabled) return;
        for (BackgroundTask task : repository.due()) transfer(task);
    }

    private void transfer(BackgroundTask task) {
        UUID lease = UUID.randomUUID();
        if (!repository.claim(task.id(), lease)) return;
        try {
            // Compatibility with a queued intake from an earlier rollout: never create
            // a permanent provider copy of data whose lifecycle ends after processing.
            if (task.targetType() != BackgroundTask.Target.DOCUMENT) {
                repository.complete(task.id(), lease, true);
                return;
            }
            byte[] bytes = repository.staged(task.targetType(), task.targetId());
            if (bytes == null) {
                repository.complete(task.id(), lease, true);
                return;
            }
            DocumentContentService.verify(task, bytes);
            store.put(task.storageKey(), bytes, task.sha256());
            DocumentContentService.verify(task, store.get(task.storageKey()));
            repository.complete(task.id(), lease, false);
        } catch (RuntimeException e) {
            repository.fail(task.id(), lease, task.attempts());
            log.warn("Private document transfer failed; task={}, attempt={}", task.id(), task.attempts() + 1);
        }
    }
}
