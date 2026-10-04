package com.codewalnut.ats.dto;

import java.util.List;

/** Interview questions for staff (INT-21…): never sent to candidates or clients. */
public final class InterviewGuideDtos {

    private InterviewGuideDtos() {}

    /** level: F (fresher), J (1–3 years) or S (3+ years); language: set for language-specific questions. */
    public record InterviewQuestion(String level, String topic, String question, String strong, String redFlags, String language) {}

    public record InterviewCategory(String id, String name, String intro, List<InterviewQuestion> questions) {}

    public record ScaleRow(int score, String label, String evidence) {}

    /** A role from the role tests, with the question categories that suit it. */
    public record InterviewRole(String id, String name, List<String> categories) {}

    public record InterviewGuide(List<String> howTo, List<ScaleRow> scale, List<InterviewRole> roles, List<InterviewCategory> categories) {}
}
