package com.codewalnut.ats.client;

/** No ANTHROPIC_API_KEY configured: the assistant is switched off. */
public class DisabledAssistantClient implements AssistantClient {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public AssistantPlan plan(Request request) {
        throw new CalendarException("The AI assistant isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
    }
}
