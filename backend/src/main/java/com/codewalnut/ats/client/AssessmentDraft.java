package com.codewalnut.ats.client;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Test questions drafted by the AI for a person to review before use (ADR-0011). */
public record AssessmentDraft(
        @JsonPropertyDescription("The drafted questions")
        List<Question> questions) {

    public record Question(
            @JsonPropertyDescription("SINGLE_CHOICE (exactly one right option), MULTI_CHOICE (two or more right options) or SHORT_ANSWER (a short exact answer such as a number or the printed output)")
            String kind,
            @JsonPropertyDescription("The question in plain English, without the code")
            String prompt,
            @JsonPropertyDescription("Code to read for this question (no markdown fences), or empty")
            String code,
            @JsonPropertyDescription("For choice questions: 4 options, each short. Empty for SHORT_ANSWER.")
            List<String> options,
            @JsonPropertyDescription("For choice questions: zero-based indexes of the right options. Empty for SHORT_ANSWER.")
            List<Integer> correctOptions,
            @JsonPropertyDescription("For SHORT_ANSWER: the accepted answers exactly as a candidate would type them (one or a few variants). Empty for choice questions.")
            List<String> acceptedAnswers,
            @JsonPropertyDescription("One or two sentences explaining the right answer, for the reviewer")
            String explanation,
            @JsonPropertyDescription("Points, 1 for easy, 2 for medium, 3 for hard")
            Integer points,
            @JsonPropertyDescription("For data-interpretation questions only: the chart or table the question is about (the app draws it). Otherwise null.")
            Chart chart) {

        public Question(String kind, String prompt, String code, List<String> options, List<Integer> correctOptions,
                List<String> acceptedAnswers, String explanation, Integer points) {
            this(kind, prompt, code, options, correctOptions, acceptedAnswers, explanation, points, null);
        }
    }

    /** Data for a picture the app draws. Pie values are percentages adding up to 100. */
    public record Chart(
            @JsonPropertyDescription("BAR, LINE, PIE or TABLE") String type,
            @JsonPropertyDescription("Chart title") String title,
            @JsonPropertyDescription("Unit for the values, e.g. \"Units\", or empty") String unit,
            @JsonPropertyDescription("BAR/LINE/PIE: category labels (3 to 7)") List<String> labels,
            @JsonPropertyDescription("BAR/LINE/PIE: whole-number values, one per label") List<Integer> values,
            @JsonPropertyDescription("TABLE: column headers") List<String> headers,
            @JsonPropertyDescription("TABLE: rows of cells, same length as headers") List<List<String>> rows) {}
}
