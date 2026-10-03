package com.codewalnut.ats.dto;

import com.codewalnut.ats.client.BrandedResume;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CodeWalnut-branded résumés (ADR-0012). */
public final class CodeWalnutResumeDtos {

    private CodeWalnutResumeDtos() {}

    /**
     * exists: a draft has been made. screening: the lines "CodeWalnut screening" would show (passed
     * tests). sourceFileName: the original résumé it was made from. savedDocumentId: the last PDF
     * saved to the candidate's documents.
     */
    public record DraftResponse(
            boolean exists, boolean aiAvailable, BrandedResume resume, boolean showEmail, boolean includeScreening,
            List<String> screening, String sourceFileName, UUID savedDocumentId, String updatedBy, Instant updatedAt) {}

    public record UpdateRequest(@NotNull BrandedResume resume, boolean showEmail, boolean includeScreening) {}

    public record SavedResponse(UUID documentId, String fileName) {}
}
