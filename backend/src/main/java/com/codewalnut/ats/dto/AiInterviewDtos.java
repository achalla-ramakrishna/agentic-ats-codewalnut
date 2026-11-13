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

    /**
     * durationMinutes: the whole interview's time limit once started. questionSet: 1–4 (Set A–D) of the
     * hand-written senior Java sets; each covers the same skills with different questions.
     */
    public record CreateInterviewRequest(@NotNull UUID jobId, @Min(20) @Max(180) Integer durationMinutes,
            @Min(1) @Max(4) Integer questionSet) {

        public CreateInterviewRequest(UUID jobId, Integer durationMinutes) {
            this(jobId, durationMinutes, null);
        }
    }

    /** notes: e.g. "The AI was unavailable, so the standard question set was used." */
    public record CreateInterviewResult(AssessmentDtos.AssessmentDetail interview, List<String> notes) {}

    /** A question of a hand-written set, as staff preview it (answers and reviewer notes included). */
    public record SetQuestion(String phase, String phaseLabel, String kind, String prompt, String code, List<String> options,
            Integer correctOption, List<String> acceptedAnswers, Integer maxSeconds, String lookFor) {}

    public record QuestionSetPreview(int set, String letter, List<SetQuestion> questions, int minutes) {}

    /**
     * Space for interview videos. perInterviewBytes: the average so far (an estimate until a few are in);
     * roomFor: about how many more interviews fit; warning: say so before candidates hit upload errors.
     */
    public record Storage(long usedBytes, long freeBytes, long totalBytes, int interviews, long perInterviewBytes, long roomFor,
            boolean temporary, String warning) {}

    /** Videos of rejected or withdrawn candidates in an opening, which can be deleted to free space. */
    public record Cleanup(int candidates, int videos, long bytes) {}

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
