package com.codewalnut.ats.domain;

import java.time.Instant;
import java.util.UUID;

/** Durable private-storage transfer and verified manifest; never contains file contents. */
public record BackgroundTask(UUID id, Target targetType, UUID targetId, String storageKey,
        String sha256, long sizeBytes, String status, int attempts, Instant verifiedAt) {
    public enum Target {
        DOCUMENT("candidate_document", "documents"), INTAKE("resume_intake", "intakes");

        private final String table;
        private final String prefix;

        Target(String table, String prefix) {
            this.table = table;
            this.prefix = prefix;
        }

        public String table() { return table; }
        public String key(UUID id) { return prefix + "/" + id; }
    }
}
