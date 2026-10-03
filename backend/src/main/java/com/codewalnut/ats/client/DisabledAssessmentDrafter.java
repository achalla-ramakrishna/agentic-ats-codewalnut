package com.codewalnut.ats.client;

/** No ANTHROPIC_API_KEY configured: questions are written by hand. */
public class DisabledAssessmentDrafter implements AssessmentDrafter {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public AssessmentDraft draft(Request request) {
        throw new CalendarException("AI question drafting isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
    }
}
