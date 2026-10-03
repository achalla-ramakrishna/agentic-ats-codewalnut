package com.codewalnut.ats.client;

import java.util.List;

/** Drafts test questions for a person to review (ADR-0011). Nothing drafted is used unreviewed. */
public interface AssessmentDrafter {

    /** avoid: prompts already in the test, so drafts don't repeat them. */
    record Request(String category, String title, String topic, String level, int count, List<String> avoid) {}

    boolean available();

    /** @throws CalendarException with a message to show when drafting fails */
    AssessmentDraft draft(Request request);
}
