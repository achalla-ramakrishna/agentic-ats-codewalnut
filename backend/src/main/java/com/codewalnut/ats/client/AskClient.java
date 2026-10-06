package com.codewalnut.ats.client;

import java.util.List;
import java.util.Map;

/**
 * Ask ATS (ASK-01…, ADR-0021): answers a staff member's question about the ATS, looking things up
 * with read-only tools that the caller supplies. The tools run as the person asking, so the answer
 * can only contain what they could already see in the app.
 */
public interface AskClient {

    boolean available();

    String model();

    /**
     * @param history earlier turns, oldest first, ending with the new question
     * @param guide how the ATS works, for "how do I…" questions
     */
    Answer answer(List<Turn> history, String guide, List<ToolSpec> tools, ToolBox toolBox);

    record Turn(boolean fromUser, String text) {}

    /** A tool's JSON schema: properties maps each input name to its schema, e.g. {"type": "string"}. */
    record ToolSpec(String name, String description, Map<String, Object> properties, List<String> required) {}

    /** Runs a tool and returns its result as JSON text; throws IllegalArgumentException for bad input. */
    interface ToolBox {
        String call(String name, Map<String, Object> input);
    }

    record Answer(String text, List<String> toolsUsed) {}
}
