package com.codewalnut.ats.client;

import java.util.List;

/** Ask ATS without an AI key: off. */
public class DisabledAskClient implements AskClient {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String model() {
        return null;
    }

    @Override
    public Answer answer(List<Turn> history, String guide, List<ToolSpec> tools, ToolBox toolBox) {
        throw new CalendarException("Ask ATS needs the AI to be set up (ANTHROPIC_API_KEY).");
    }
}
