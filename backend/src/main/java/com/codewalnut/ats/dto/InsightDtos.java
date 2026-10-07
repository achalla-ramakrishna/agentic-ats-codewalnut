package com.codewalnut.ats.dto;

import com.codewalnut.ats.client.ResumeInsight;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Résumé intelligence (ADR-0010). Everything here is advisory; people decide. */
public final class InsightDtos {

    private InsightDtos() {}

    /**
     * met/partial/total: requirement counts behind fitPercent. stale: the opening changed since.
     * skills, projects, experienceMonths and graduationYear drive the filters on the opening page.
     */
    public record InsightSummary(
            UUID applicationId, String status, Integer fitPercent, String headline, String error, boolean stale,
            int met, int partial, int total, List<String> skills, int projects, int experienceMonths,
            Integer graduationYear) {}

    /** readiness: for "closest to selection", 0–100 from interview feedback, test and résumé match (AI-32). */
    public record Suggestion(UUID applicationId, String candidateName, String stageLabel, Integer fitPercent,
            String reason, Integer readiness) {}

    /**
     * available: AI reading is switched on. hasDescription: the opening has a description to match
     * against (no description, no match score). notAnalyzed: candidates with no reading yet.
     */
    public record InsightsResponse(
            boolean available, boolean hasDescription, int analyzed, int pending, int failed, int noResume,
            int notAnalyzed, List<InsightSummary> insights, List<Suggestion> contactNext,
            List<Suggestion> closestToSelection) {}

    public record InsightDetail(
            UUID applicationId, String status, Integer fitPercent, String headline, String error, boolean stale,
            String model, Instant analyzedAt, String documentFileName, ResumeInsight profile) {}

    public record AnalyzeResult(int queued, int noResume, int upToDate) {}

    public record IntakeItem(UUID id, String fileName, String status, String outcome, UUID applicationId,
            String candidateName, String error) {}

    public record IntakeProgress(int total, int pending, int done, int failed, int newCandidates, int existing,
            List<IntakeItem> items) {}
}
