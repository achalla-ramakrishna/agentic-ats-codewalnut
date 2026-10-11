package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.naukri.NaukriProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Ranking a Naukri applicant export against an opening and adding the chosen people (ADR-0026). */
public final class NaukriDtos {

    private NaukriDtos() {}

    /** NEW and EXISTING_CANDIDATE rows can be added; the others are shown with the reason. */
    public enum RowStatus { NEW, EXISTING_CANDIDATE, ALREADY_IN_OPENING, DUPLICATE_IN_FILE, NO_CONTACT }

    public record RankedRow(NaukriProfile profile, int matchPercent, String summary, List<String> strengths,
            List<String> gaps, RowStatus status) {}

    /**
     * rankedBy: "AI" or "RULES". note: anything to tell the recruiter about the ranking (AI off,
     * some rows ranked by rules). Nothing from the file is stored until rows are added.
     */
    public record RankResult(String fileName, String rankedBy, String note, boolean hasDescription, int total,
            List<String> missingColumns, List<RankedRow> rows) {}

    public record ImportItem(@NotNull @Valid NaukriProfile profile, @Min(0) @Max(100) Integer matchPercent,
            @Size(max = 500) String summary) {}

    public record ImportRequest(@NotNull Stage stage, @NotEmpty @Size(max = 1000) List<@Valid ImportItem> rows) {}

    public record AddedRow(UUID applicationId, String name, String email, boolean hasEmail) {}

    public record ImportResult(int added, List<AddedRow> rows, List<String> skipped) {}
}
