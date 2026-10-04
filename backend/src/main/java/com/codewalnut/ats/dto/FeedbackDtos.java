package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.InterviewFeedback;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Interview feedback (INT-24…): staff only. */
public final class FeedbackDtos {

    private FeedbackDtos() {}

    /** rating: 1–4, or null when the competency wasn't assessed. */
    public record Rating(@NotBlank @Size(max = 100) String competency, @Min(1) @Max(4) Integer rating, @Size(max = 2000) String note) {}

    public record FeedbackRequest(
            @NotNull InterviewFeedback.Attendance attendance,
            @Size(max = 12) List<@Valid Rating> ratings,
            @Size(max = 5000) String strengths,
            @Size(max = 5000) String concerns,
            @Size(max = 5000) String questionsAsked,
            InterviewFeedback.Recommendation recommendation,
            @Size(max = 5000) String notes) {}

    public record FeedbackView(
            String authorEmail, String authorName, InterviewFeedback.Attendance attendance, List<Rating> ratings,
            Double averageRating, String strengths, String concerns, String questionsAsked,
            InterviewFeedback.Recommendation recommendation, String notes, Instant submittedAt, Instant updatedAt) {}

    /** A competency to rate, with what each level looks like. */
    public record Competency(String name, String guidance) {}

    /**
     * The feedback page for one interview. others: the rest of the panel's feedback, shown only once
     * you have submitted yours (or if you aren't on the panel); hiddenCount: how many are held back.
     */
    public record InterviewFeedbackPage(
            InterviewResponse interview, boolean onPanel, boolean canSubmit, FeedbackView mine, List<FeedbackView> others,
            int hiddenCount, List<Competency> competencies) {}

    /** One line per interview in the candidate's panel: who has given feedback and what they recommend. */
    public record FeedbackSummary(java.util.UUID interviewId, int submitted, int panelSize, List<InterviewFeedback.Recommendation> recommendations,
            boolean mineSubmitted, boolean visible) {}
}
