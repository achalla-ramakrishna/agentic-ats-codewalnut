package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.dto.MessageDtos.MessageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Online tests (ADR-0011). Answers are only ever in staff responses, never candidate ones. */
public final class AssessmentDtos {

    private AssessmentDtos() {}

    // ---- the test library (staff) ----

    public record AssessmentSummary(
            UUID id, String title, Assessment.Category category, String description, int durationMinutes,
            int passPercent, Assessment.Status status, int questionCount, int totalPoints, long invites,
            Instant updatedAt) {}

    public record QuestionView(
            UUID id, int position, AssessmentQuestion.Kind kind, String prompt, String code, List<String> options,
            List<Integer> correct, List<String> acceptedAnswers, int points, String explanation, boolean aiDrafted) {}

    public record AssessmentDetail(AssessmentSummary summary, List<QuestionView> questions) {}

    public record CreateAssessmentRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull Assessment.Category category,
            @Size(max = 5000) String description,
            @Min(5) @Max(180) int durationMinutes,
            @Min(0) @Max(100) int passPercent) {}

    public record UpdateAssessmentRequest(
            @Size(min = 1, max = 200) String title,
            Assessment.Category category,
            @Size(max = 5000) String description,
            @Min(5) @Max(180) Integer durationMinutes,
            @Min(0) @Max(100) Integer passPercent) {}

    /**
     * correct: zero-based option indexes (choice questions). acceptedAnswers: what counts as right
     * for a short answer (compared ignoring case and extra spaces).
     */
    public record QuestionRequest(
            @NotNull AssessmentQuestion.Kind kind,
            @NotBlank @Size(max = 5000) String prompt,
            @Size(max = 5000) String code,
            @Size(max = 8) List<@Size(max = 500) String> options,
            @Size(max = 8) List<Integer> correct,
            @Size(max = 10) List<@Size(max = 500) String> acceptedAnswers,
            @Min(1) @Max(10) int points,
            @Size(max = 2000) String explanation) {}

    public record DraftRequest(@Size(max = 500) String topic, @Size(max = 50) String level, @Min(1) @Max(15) int count) {}

    public record DraftResult(int added, List<String> notes, AssessmentDetail assessment) {}

    // ---- sending and results (staff) ----

    public record SendTestRequest(
            @NotNull UUID assessmentId,
            @Min(1) @Max(14) int dueDays,
            boolean sendEmail,
            boolean sendWhatsApp,
            @Size(max = 2000) String note) {}

    public record RemindRequest(boolean sendEmail, boolean sendWhatsApp) {}

    /** needsNudge: sent two or more days ago (or since the last reminder) and not started. */
    public record InviteView(
            UUID id, UUID applicationId, UUID assessmentId, String title, Assessment.Category category,
            AssessmentInvite.Status status, String sentBy, Instant sentAt, Instant dueAt, Instant startedAt,
            Instant submittedAt, Integer score, Integer maxScore, Integer percent, Boolean passed, int passPercent,
            int reminderCount, Instant lastRemindedAt, boolean needsNudge) {}

    public record SendResult(InviteView invite, MessageResponse message) {}

    public record AnswerReview(
            int position, AssessmentQuestion.Kind kind, String prompt, String code, List<String> options,
            List<String> given, List<Integer> correct, List<String> acceptedAnswers, int points, int earned) {}

    public record InviteDetail(InviteView invite, List<AnswerReview> answers) {}

    // ---- the candidate ----

    public record MyTest(
            UUID id, String title, Assessment.Category category, String description, String jobTitle,
            int questionCount, int durationMinutes, AssessmentInvite.Status status, Instant dueAt,
            Instant startedAt, Instant deadlineAt, Instant submittedAt) {}

    public record CandidateQuestion(
            UUID id, int position, AssessmentQuestion.Kind kind, String prompt, String code, List<String> options,
            int points) {}

    /** answers: what the candidate has saved so far, by question id. */
    public record TakeTest(MyTest test, List<CandidateQuestion> questions, Map<UUID, List<String>> answers,
            long secondsLeft) {}

    public record SaveAnswersRequest(@NotNull @Size(max = 200) Map<UUID, @Size(max = 10) List<@Size(max = 500) String>> answers) {}
}
