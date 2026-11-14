package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.AiInterviewVerdict.Decision;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.Stage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** People's verdicts on completed AI interviews (AINT-21): does the candidate go to the next round? */
public final class AiReviewDtos {

    private AiReviewDtos() {}

    /** A mark on one answer (by question position): GOOD, OK or WEAK, with an optional note. */
    public record AnswerMark(int position, @Pattern(regexp = "GOOD|OK|WEAK") String mark, @Size(max = 1000) String note) {}

    public record VerdictRequest(@NotNull Decision decision, @Size(max = 4000) String feedback,
            @Valid @Size(max = 100) List<AnswerMark> marks) {}

    public record VerdictView(String reviewerName, String reviewerEmail, Decision decision, String decisionLabel,
            String feedback, List<AnswerMark> marks, Instant updatedAt, boolean mine) {}

    /** Who decided what, for lists. */
    public record VerdictBrief(String reviewerName, Decision decision) {}

    /** A completed AI interview in the review list. mine: the signed-in person's decision, if any. */
    public record QueueRow(UUID inviteId, String candidateName, UUID jobId, String jobTitle, Stage stage, Instant submittedAt,
            Integer aiScore, AssessmentInvite.Evaluation evaluation, Decision mine, List<VerdictBrief> verdicts) {}

    public record Queue(List<QueueRow> interviews, int toReview) {}

    /** One interview to review: the answers (videos, transcripts, written answers), the AI review and everyone's verdicts. */
    public record ReviewPage(UUID inviteId, String candidateName, UUID jobId, String jobTitle, Stage stage,
            AssessmentDtos.InviteDetail detail, List<VerdictView> verdicts) {}
}
