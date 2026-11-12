package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.Stage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** AI interviews (ADR-0030): building one for an opening, video answers, the AI review and the ranking. */
public final class AiInterviewDtos {

    private AiInterviewDtos() {}

    /** durationMinutes: the whole interview's time limit once started. */
    public record CreateInterviewRequest(@NotNull UUID jobId, @Min(20) @Max(180) Integer durationMinutes) {}

    /** notes: e.g. "The AI was unavailable, so the standard question set was used." */
    public record CreateInterviewResult(AssessmentDtos.AssessmentDetail interview, List<String> notes) {}

    public record VideoSaved(UUID videoId, int seconds, long sizeBytes) {}

    /** One area of the review: rating 1–5 (0 = nothing to judge on), with the reason and a quote. */
    public record AreaView(String area, String label, int weight, int rating, String reason, String evidence) {}

    public record PhaseView(String phase, String label, int rating, String note) {}

    /**
     * The advisory review of one interview. usesAi: false for the basic review by rules. aiScore is
     * worked out by the app from the area ratings (by weight) and the marked answers.
     */
    public record InterviewReview(
            AssessmentInvite.Evaluation status, boolean usesAi, Integer aiScore, Integer markedPercent,
            List<AreaView> areas, List<PhaseView> phases, String summary, List<String> strengths, List<String> toCheck,
            String noticePeriod, String expectedSalary, Instant reviewedAt) {}

    /** A row of the opening's AI interview ranking. areas: rating per area (TECHNICAL → 4). */
    public record RankedCandidate(
            UUID inviteId, UUID applicationId, String candidateName, Stage stage, AssessmentInvite.Status status,
            AssessmentInvite.Evaluation evaluation, boolean usesAi, Integer aiScore, Integer markedPercent,
            Map<String, Integer> areas, String summary, String noticePeriod, String expectedSalary,
            Instant sentAt, Instant submittedAt, int videos) {}

    public record JobInterviews(List<RankedCandidate> candidates, int sent, int started, int submitted, int reviewed) {}
}
