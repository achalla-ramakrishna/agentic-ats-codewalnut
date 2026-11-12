package com.codewalnut.ats.client;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The AI interview agent (ADR-0030): drafts an opening's interview questions and reviews submitted
 * interviews. Advisory only: people edit the questions, and the review ranks and explains, it never
 * moves or rejects anyone. The app (not the model) turns the ratings into the score.
 */
public interface InterviewAgent {

    /** The areas every interview is rated on, with their weight in the app's score. */
    enum Area {
        TECHNICAL("Technical depth", 30),
        ARCHITECTURE("Architecture & design", 20),
        COMMUNICATION("Communication", 20),
        EXPERIENCE("Experience fit", 15),
        READINESS("Readiness to join", 15);

        private final String label;
        private final int weight;

        Area(String label, int weight) {
            this.label = label;
            this.weight = weight;
        }

        public String label() {
            return label;
        }

        public int weight() {
            return weight;
        }
    }

    record Job(String title, String description) {}

    // ---- drafting ----

    record Draft(List<DraftQuestion> questions) {}

    record DraftQuestion(
            @JsonPropertyDescription("Phase: INTRO, EXPERIENCE, SKILLS, PROJECTS, ARCHITECTURE, TECHNICAL, SOFT_SKILLS or READINESS") String phase,
            @JsonPropertyDescription("VIDEO (spoken answer on camera), WRITTEN (short typed answer) or FILL_BLANK (one word or phrase)") String kind,
            @JsonPropertyDescription("The question as the candidate reads it. For FILL_BLANK, mark the blank as ____") String prompt,
            @JsonPropertyDescription("FILL_BLANK only: the accepted answers (1 to 3 spellings); empty otherwise") List<String> acceptedAnswers,
            @JsonPropertyDescription("VIDEO only: the longest answer in seconds, 60 to 180; 0 otherwise") int maxSeconds) {}

    // ---- reviewing ----

    /** One answer as the reviewer sees it. answer: the transcript, the typed text, or the option picked; correct: marked answers only. */
    record Answer(String phase, String kind, String question, String answer, Integer seconds, Boolean correct) {}

    record Review(
            @JsonPropertyDescription("One rating per area: TECHNICAL, ARCHITECTURE, COMMUNICATION, EXPERIENCE, READINESS") List<AreaRating> areas,
            @JsonPropertyDescription("One short note per phase that had answers") List<PhaseNote> phases,
            @JsonPropertyDescription("Two or three sentences for the recruiter: who this candidate is and how they came across") String summary,
            @JsonPropertyDescription("Up to 4 job-related strengths, each backed by something they said") List<String> strengths,
            @JsonPropertyDescription("Up to 4 things to check in a live interview, phrased as 'not evident' rather than 'lacks'") List<String> toCheck,
            @JsonPropertyDescription("Notice period as stated by the candidate, or empty if not said") String noticePeriod,
            @JsonPropertyDescription("Expected salary as stated by the candidate, or empty if not said") String expectedSalary) {}

    record AreaRating(
            @JsonPropertyDescription("TECHNICAL, ARCHITECTURE, COMMUNICATION, EXPERIENCE or READINESS") String area,
            @JsonPropertyDescription("1 (weak) to 5 (excellent); 0 when the answers give nothing to judge it on") int rating,
            @JsonPropertyDescription("One sentence: why this rating") String reason,
            @JsonPropertyDescription("A short quote or close paraphrase from the answers that supports it, or empty") String evidence) {}

    record PhaseNote(
            @JsonPropertyDescription("The phase, as given") String phase,
            @JsonPropertyDescription("1 to 5; 0 if unanswered") int rating,
            @JsonPropertyDescription("One short sentence") String note) {}

    /** True when this agent uses a model; false for the built-in rules. */
    boolean usesAi();

    String model();

    /** @throws CalendarException with a message to show when drafting fails */
    Draft draft(Job job);

    /** @throws CalendarException when the review can't be done now (it is retried) */
    Review review(Job job, List<Answer> answers);
}
