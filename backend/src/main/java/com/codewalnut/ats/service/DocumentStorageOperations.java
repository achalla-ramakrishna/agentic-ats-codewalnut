package com.codewalnut.ats.service;

import com.codewalnut.ats.config.DocumentStorageProperties;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentStorageOperations {
    private final DocumentStorageRepository repository;
    private final DocumentStorageProperties properties;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;

    public void requireOperator(AppUser actor) {
        accessPolicy.require(actor, Capability.MANAGE_USERS);
        if (!properties.enabled() || !properties.operationsEnabled()) throw new NotFoundException("Operation unavailable");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> status(AppUser actor) {
        requireOperator(actor);
        return repository.counts();
    }

    @Transactional
    public Map<String, Integer> enqueue(AppUser actor, int limit) {
        requireOperator(actor);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        int count = 0;
        for (Target type : Target.values()) {
            for (UUID id : repository.unqueued(type, limit)) {
                byte[] data = repository.staged(type, id);
                if (data != null) {
                    repository.enqueue(type, id, data, DocumentContentService.sha256(data));
                    count++;
                }
            }
        }
        audit.record(actor, AuditAction.DOCUMENT_STORAGE_MIGRATION, null, null, Map.of("queued", count));
        return Map.of("queued", count);
    }

    @Transactional
    public Map<String, Integer> retry(AppUser actor) {
        requireOperator(actor);
        int count = repository.retryFailed();
        audit.record(actor, AuditAction.DOCUMENT_STORAGE_MIGRATION, null, null, Map.of("retried", count));
        return Map.of("retried", count);
    }
}
