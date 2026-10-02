package com.codewalnut.ats.client;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the AI assistant proposes for one instruction. The JSON schema sent to Claude
 * (structured output) is derived from these records. Nothing here is acted on until a person
 * reviews and applies it (ADR-0009).
 */
public record AssistantPlan(
        @JsonPropertyDescription("One short sentence in plain words saying what you understood, e.g. \"Move 3 candidates to Shortlisted.\"")
        String summary,
        @JsonPropertyDescription("Actions for candidates you could identify with confidence. Empty if none.")
        List<Action> actions,
        @JsonPropertyDescription("Names in the instruction you could not match to exactly one candidate. Empty if none.")
        List<Unresolved> unresolved) {

    public record Action(
            @JsonPropertyDescription("MOVE_STAGE or ADD_NOTE")
            String type,
            @JsonPropertyDescription("The applicationId of the candidate, copied exactly from the candidate list")
            String applicationId,
            @JsonPropertyDescription("For MOVE_STAGE: the stage key from the stage list. Empty for ADD_NOTE.")
            String stage,
            @JsonPropertyDescription("For ADD_NOTE: the note. For MOVE_STAGE: the reason if the instruction gives one, else empty.")
            String note) {}

    public record Unresolved(
            @JsonPropertyDescription("The name or words from the instruction, as written")
            String mention,
            @JsonPropertyDescription("applicationIds of candidates it could mean (two or more), or empty if nobody matches")
            List<String> possibleApplicationIds,
            @JsonPropertyDescription("The stage key the instruction wanted for this person, or empty")
            String stage,
            @JsonPropertyDescription("The note or reason the instruction gave for this person, or empty")
            String note) {}
}
