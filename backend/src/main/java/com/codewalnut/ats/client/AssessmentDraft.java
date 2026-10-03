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
            Integer points) {}
}
