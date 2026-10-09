package com.codewalnut.ats.dto;

import java.time.Instant;

public final class DocumentStorageCleanupDtos {
    private DocumentStorageCleanupDtos() {}

    public record Request(String confirmation, String backupReference, Boolean restoreVerified,
            Instant observedBefore, Integer limit, Boolean dryRun) {
        public int batchSize() { return limit == null ? 1 : limit; }
        public boolean isDryRun() { return dryRun == null || dryRun; }
    }

    /** Counts only: no document identifiers, filenames or provider references leave the operator API. */
    public record Result(int scanned, int eligible, int cleaned, int skipped, int failed) {}
}
