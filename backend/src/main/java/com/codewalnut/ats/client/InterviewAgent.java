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
            @JsonPropertyDescription("Optional code the candidate reads with the question (e.g. a snippet to review); empty if none") String code,
            @JsonPropertyDescription("Leave empty") List<String> options,
            @JsonPropertyDescription("Leave as -1") int correctOption,
            @JsonPropertyDescription("FILL_BLANK only: the accepted answers (1 to 3 spellings); empty otherwise") List<String> acceptedAnswers,
            @JsonPropertyDescription("VIDEO only: the longest answer in seconds, 60 to 180; 0 otherwise") int maxSeconds,
            @JsonPropertyDescription("For reviewers only, never shown to candidates: the 3 to 5 points a strong answer covers") String goodAnswer) {

        /** A question without code, options or reviewer notes. */
        public DraftQuestion(String phase, String kind, String prompt, List<String> acceptedAnswers, int maxSeconds) {
            this(phase, kind, prompt, "", List.of(), -1, acceptedAnswers, maxSeconds, "");
        }
    }

    // ---- reviewing ----

    /**
     * One answer as the reviewer sees it. answer: the transcript, the typed text, or the option picked; correct:
     * marked answers only; goodAnswer: what the hiring team expects a strong answer to cover, if they wrote it.
     */
    record Answer(String phase, String kind, String question, String answer, Integer seconds, Boolean correct, String goodAnswer,
            String signals) {

        public Answer(String phase, String kind, String question, String answer, Integer seconds, Boolean correct) {
            this(phase, kind, question, answer, seconds, correct, null, null);
        }

        public Answer(String phase, String kind, String question, String answer, Integer seconds, Boolean correct, String goodAnswer) {
            this(phase, kind, question, answer, seconds, correct, goodAnswer, null);
        }
    }

    record Review(
            @JsonPropertyDescription("One rating per area: TECHNICAL, ARCHITECTURE, COMMUNICATION, EXPERIENCE, READINESS") List<AreaRating> areas,
            @JsonPropertyDescription("One short note per phase that had answers") List<PhaseNote> phases,
            @JsonPropertyDescription("Two or three sentences for the recruiter: who this candidate is and how they came across") String summary,
            @JsonPropertyDescription("Up to 4 job-related strengths, each backed by something they said") List<String> strengths,
            @JsonPropertyDescription("Up to 4 things to check in a live interview, phrased as 'not evident' rather than 'lacks'") List<String> toCheck,
            @JsonPropertyDescription("Notice period as stated by the candidate, or empty if not said") String noticePeriod,
            @JsonPropertyDescription("Expected salary as stated by the candidate, or empty if not said") String expectedSalary,
            @JsonPropertyDescription("If several spoken answers sound read out from prepared or generated text rather than answered in "
                    + "their own words, one or two sentences saying which questions and why; empty otherwise") String delivery) {

        public Review(List<AreaRating> areas, List<PhaseNote> phases, String summary, List<String> strengths, List<String> toCheck,
                String noticePeriod, String expectedSalary) {
            this(areas, phases, summary, strengths, toCheck, noticePeriod, expectedSalary, null);
        }
    }

    record AreaRating(
            @JsonPropertyDescription("TECHNICAL, ARCHITECTURE, COMMUNICATION, EXPERIENCE or READINESS") String area,
            @JsonPropertyDescription("1 (weak) to 5 (excellent); 0 when the answers give nothing to judge it on") int rating,
            @JsonPropertyDescription("One sentence: why this rating") String reason,
            @JsonPropertyDescription("A short quote or close paraphrase from the answers that supports it, or empty") String evidence) {}

    record PhaseNote(
            @JsonPropertyDescription("The phase, as given") String phase,
            @JsonPropertyDescription("1 to 5; 0 if unanswered") int rating,
            @JsonPropertyDescription("One short sentence") String note) {}

    // ---- reading check, one answer at a time (AINT-24, ADR-0031) ----

    /** One spoken answer, checked as soon as it uploads, while the interview is still running. */
    record ReadCheckInput(String question, String transcript, int seconds) {}

    record ReadCheck(
            @JsonPropertyDescription("LIKELY (several clear signs it was read from prepared or generated text), POSSIBLE (some signs), "
                    + "or UNLIKELY (sounds like their own words)") String verdict,
            @JsonPropertyDescription("One or two sentences for the hiring team: the signs you saw, quoting a short phrase") String reason) {}

    /**
     * Whether a spoken answer sounds read out rather than answered in the candidate's own words. Advisory: it
     * alerts people, who watch the video and decide. Null when this agent can't judge it (no model).
     *
     * @throws CalendarException when the check can't be done now
     */
    default ReadCheck readCheck(Job job, ReadCheckInput answer) {
        return null;
    }

    /** True when this agent uses a model; false for the built-in rules. */
    boolean usesAi();

    String model();

    /** @throws CalendarException with a message to show when drafting fails */
    Draft draft(Job job);

    /** @throws CalendarException when the review can't be done now (it is retried) */
    Review review(Job job, List<Answer> answers);
}
