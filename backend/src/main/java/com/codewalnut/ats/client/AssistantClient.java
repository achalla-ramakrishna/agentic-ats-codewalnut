package com.codewalnut.ats.client;

import java.util.List;

/** Turns a recruiter's plain-words instruction about one opening into a proposed plan (ADR-0009). */
public interface AssistantClient {

    record Candidate(String applicationId, String name, String stageLabel) {}

    record StageOption(String key, String label) {}

    record Request(String instruction, String openingTitle, List<Candidate> candidates, List<StageOption> stages) {}

    boolean available();

    /** @throws CalendarException (shown to the user) when the assistant can't answer */
    AssistantPlan plan(Request request);
}
