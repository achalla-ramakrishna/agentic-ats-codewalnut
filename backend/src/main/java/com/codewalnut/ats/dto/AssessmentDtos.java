package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.dto.MessageDtos.MessageResponse;
import jakarta.validation.Valid;
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
            Instant updatedAt, long taken) {}

    /** openInvites: candidates who were sent the test but haven't started; their links stop working. */
    public record DeleteResult(String title, int openInvites) {}

    /** figure: SVG or image data URI shown with the question; optionFigures: pictures for the options, or null. */
    public record QuestionView(
            UUID id, int position, AssessmentQuestion.Kind kind, String prompt, String code, List<String> options,
            List<Integer> correct, List<String> acceptedAnswers, int points, String explanation, boolean aiDrafted,
            String figure, List<String> optionFigures, String section, String topic, String difficulty, CodingView coding) {}

    // ---- coding questions (ADR-0016) ----

    /** What goes to the program's stdin, and what it should print. */
    public record TestCase(@Size(max = 200_000) String input, @Size(max = 200_000) String output) {}

    /**
     * The part of a coding question candidates see: languages they may use, starter code per
     * language, sample tests (with expected output) and limits. Hidden tests are never in here.
     */
    public record CodingSpec(List<String> languages, Map<String, String> starter, List<TestCase> samples,
            double timeLimitSeconds, int memoryMb, String inputFormat, String outputFormat, String constraints) {}

    /** Writing or editing a coding question: the public spec plus the hidden tests. */
    public record CodingRequest(
            @Size(max = 4) List<String> languages,
            @Size(max = 4) Map<String, @Size(max = 20_000) String> starter,
            @Size(max = 10) List<@Valid TestCase> samples,
            @Size(max = 60) List<@Valid TestCase> tests,
            Double timeLimitSeconds,
            Integer memoryMb,
            @Size(max = 3000) String inputFormat,
            @Size(max = 3000) String outputFormat,
            @Size(max = 3000) String constraints) {}

    /** Staff view of a coding question: the spec and the hidden tests. */
    public record CodingView(CodingSpec spec, List<TestCase> tests) {}

    /** One test case run. input and expected are filled for samples, and for hidden tests in staff views only. */
    public record CaseResult(boolean sample, boolean passed, String status, String output, String error,
            Double timeSeconds, Integer memoryKb, String input, String expected) {}

    public record RunCodeRequest(@NotBlank @Size(max = 20) String language, @NotNull @Size(max = 50_000) String source) {}

    /** compileOutput: the compiler's message when the code didn't compile. */
    public record RunCodeResult(boolean compiled, String compileOutput, List<CaseResult> cases, int passed, int total,
            int runsLeft) {}

    /** How a coding answer did: tests passed and points (points × passed ÷ total, rounded). */
    public record CodeResult(String language, String source, int passed, int total, int earned, String compileOutput,
            List<CaseResult> cases) {}

    /** Browser signals while taking a test. Counts are totals so far, so a resend never double counts. */
    public record ActivityRequest(@Min(0) @Max(10_000) int tabSwitches, @Min(0) @Max(10_000) int pastes,
            @Min(0) @Max(10_000_000) int pastedChars) {}

    /** Advisory integrity signals shown to staff next to a result. */
    public record Activity(int tabSwitches, int pastes, int pastedChars, int runs) {}

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
            @Min(1) @Max(20) int points,
            @Size(max = 2000) String explanation,
            @Size(max = 1_500_000) String figure,
            @Valid CodingRequest coding) {

        public QuestionRequest(AssessmentQuestion.Kind kind, String prompt, String code, List<String> options, List<Integer> correct,
                List<String> acceptedAnswers, int points, String explanation, String figure) {
            this(kind, prompt, code, options, correct, acceptedAnswers, points, explanation, figure, null);
        }

        public QuestionRequest(AssessmentQuestion.Kind kind, String prompt, String code, List<String> options, List<Integer> correct,
                List<String> acceptedAnswers, int points, String explanation) {
            this(kind, prompt, code, options, correct, acceptedAnswers, points, explanation, null, null);
        }
    }

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
            int reminderCount, Instant lastRemindedAt, boolean needsNudge, boolean newResult, AssessmentInvite.Grading grading) {}

    /** A submitted test nobody has looked at yet, with where to find the candidate. */
    public record NewResult(InviteView invite, String candidateName, UUID jobId, String jobTitle) {}

    public record SendResult(InviteView invite, MessageResponse message) {}

    public record AnswerReview(
            int position, AssessmentQuestion.Kind kind, String prompt, String code, List<String> options,
            List<String> given, List<Integer> correct, List<String> acceptedAnswers, int points, int earned,
            String figure, List<String> optionFigures, String section, CodeResult codeResult) {}

    /** Score per section, e.g. Numerical ability 14 / 20. */
    public record SectionScore(String section, String label, int score, int max, int questions) {}

    /** activity: browser signals while the test was taken (advisory). */
    public record InviteDetail(InviteView invite, List<AnswerReview> answers, List<SectionScore> sections, Activity activity) {}

    // ---- the candidate ----

    public record MyTest(
            UUID id, String title, Assessment.Category category, String description, String jobTitle,
            int questionCount, int durationMinutes, AssessmentInvite.Status status, Instant dueAt,
            Instant startedAt, Instant deadlineAt, Instant submittedAt) {}

    public record CandidateQuestion(
            UUID id, int position, AssessmentQuestion.Kind kind, String prompt, String code, List<String> options,
            int points, String figure, List<String> optionFigures, String section, CodingSpec coding) {}

    /** answers: what the candidate has saved so far, by question id. */
    public record TakeTest(MyTest test, List<CandidateQuestion> questions, Map<UUID, List<String>> answers,
            long secondsLeft) {}

    /** Choice and short answers are capped at 500 characters each; a coding answer is [language, source]. */
    public record SaveAnswersRequest(@NotNull @Size(max = 200) Map<UUID, @Size(max = 10) List<@Size(max = 50_000) String>> answers) {}
}
