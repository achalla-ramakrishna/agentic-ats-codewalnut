package com.codewalnut.ats.service;

import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.dto.DocumentStorageCleanupDtos.Request;
import com.codewalnut.ats.dto.DocumentStorageCleanupDtos.Result;
import com.codewalnut.ats.repository.DocumentStorageCleanupRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit operator-only removal of retained bytes; never scheduled and never deletes remote objects. */
@Service
public class DocumentStorageCleanupService {
    public static final String CONFIRMATION = "REMOVE VERIFIED LEGACY DOCUMENT BYTES";
    private final DocumentStorageOperations operations;
    private final DocumentStorageCleanupRepository repository;
    private final PrivateDocumentStore store;
    private final AuditService audit;
    private final Clock clock;
    private final boolean enabled;
    private final TransactionTemplate transaction;

    public DocumentStorageCleanupService(DocumentStorageOperations operations, DocumentStorageCleanupRepository repository,
            PrivateDocumentStore store, AuditService audit, @Qualifier("documentStorageCleanupClock") Clock clock,
            @Value("${ats.document-storage.cleanup-enabled:false}") boolean enabled, PlatformTransactionManager manager) {
        this.operations = operations;
        this.repository = repository;
        this.store = store;
        this.audit = audit;
        this.clock = clock;
        this.enabled = enabled;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setTimeout(15);
    }

    /**
     * The HTTP controller has no transaction. Keep orchestration unannotated:
     * even NOT_SUPPORTED creates a synchronization scope that makes JdbcTemplate
     * retain eligible()'s connection during the subsequent provider reads.
     */
    public Result cleanup(AppUser actor, Request request) {
        operations.requireOperator(actor);
        if (!enabled) throw new NotFoundException("Operation unavailable");
        validate(request);
        var candidates = repository.eligible(request.observedBefore(), request.batchSize());
        int eligible = 0;
        int cleaned = 0;
        int skipped = 0;
        int failed = 0;
        for (var expected : candidates) {
            try {
                // This read is outside every database transaction. Immutable keys and a
                // fresh integrity check are required; historical READY alone is insufficient.
                DocumentContentService.verify(expected, store.get(expected.storageKey()));
                boolean unchanged = Boolean.TRUE.equals(transaction.execute(status -> {
                    var locked = repository.lock(expected);
                    if (locked.isEmpty() || !expected.equals(locked.get().manifest()) || locked.get().bytes() == null) return false;
                    DocumentContentService.verify(expected, locked.get().bytes());
                    if (!request.isDryRun()) {
                        if (repository.clearLegacy(expected) != 1) throw new IllegalStateException("Cleanup state changed");
                        // Deletion and audit commit together, or both roll back.
                        audit.recordInCurrentTransaction(actor, AuditAction.DOCUMENT_STORAGE_CLEANUP, "CandidateDocument", expected.targetId(),
                                Map.of("cleaned", 1, "dryRun", false, "backupReference", request.backupReference(),
                                        "observedBefore", request.observedBefore().toString(), "restoreVerified", true));
                    }
                    return true;
                }));
                if (unchanged) {
                    eligible++;
                    if (!request.isDryRun()) cleaned++;
                } else skipped++;
            } catch (RuntimeException failure) {
                // A missing/corrupt object, changed local bytes, or failed transaction
                // retains the source. Never log provider bodies, paths or file contents.
                failed++;
            }
        }
        var result = new Result(candidates.size(), eligible, cleaned, skipped, failed);
        audit.record(actor, AuditAction.DOCUMENT_STORAGE_CLEANUP, null, null,
                Map.of("dryRun", request.isDryRun(), "scanned", result.scanned(), "eligible", result.eligible(),
                        "cleaned", result.cleaned(), "skipped", result.skipped(), "failed", result.failed(),
                        "backupReference", request.backupReference(), "observedBefore", request.observedBefore().toString(),
                        "restoreVerified", true));
        return result;
    }

    private void validate(Request request) {
        if (request == null || !CONFIRMATION.equals(request.confirmation())) {
            throw new IllegalArgumentException("Exact cleanup confirmation is required");
        }
        if (!Boolean.TRUE.equals(request.restoreVerified()) || request.backupReference() == null
                || !request.backupReference().matches("[A-Za-z0-9][A-Za-z0-9._-]{2,79}")) {
            throw new IllegalArgumentException("A verified restore and nonsecret backup reference are required");
        }
        if (request.observedBefore() == null || request.observedBefore().isBefore(Instant.EPOCH)
                || request.observedBefore().isAfter(clock.instant().minus(Duration.ofDays(14)))) {
            throw new IllegalArgumentException("Observation cutoff must be at least 14 days old");
        }
        if (request.batchSize() < 1 || request.batchSize() > 10) throw new IllegalArgumentException("limit must be between 1 and 10");
    }
}
