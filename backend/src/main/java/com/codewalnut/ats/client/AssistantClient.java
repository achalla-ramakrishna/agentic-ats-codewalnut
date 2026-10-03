package com.codewalnut.ats.client;

import java.util.List;

/**
 * Turns a recruiter's plain-words instruction about one opening into a proposed plan (ADR-0009),
 * or answers a question about its candidates from their résumé readings (ADR-0010).
 */
public interface AssistantClient {

    /** profile: a short summary of the AI's résumé reading (no contact details), or null if not read. */
    record Candidate(String applicationId, String name, String stageLabel, String profile) {

        public Candidate(String applicationId, String name, String stageLabel) {
            this(applicationId, name, stageLabel, null);
        }
    }

    record StageOption(String key, String label) {}

    record Request(String instruction, String openingTitle, List<Candidate> candidates, List<StageOption> stages) {}

    boolean available();

    /** @throws CalendarException (shown to the user) when the assistant can't answer */
    AssistantPlan plan(Request request);
}
