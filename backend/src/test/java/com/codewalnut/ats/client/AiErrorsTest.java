package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AI failures say what to fix instead of a bare "unavailable". */
class AiErrorsTest {

    @Test
    void namesTheCause() {
        assertThat(AiErrors.reason(401, "invalid x-api-key")).contains("ANTHROPIC_API_KEY");
        assertThat(AiErrors.reason(400, "Your credit balance is too low to access the Anthropic API.")).contains("run out of credit");
        assertThat(AiErrors.reason(404, "model: claude-x")).contains("ATS_ASSISTANT_MODEL");
        assertThat(AiErrors.reason(529, "Overloaded")).contains("overloaded").contains("529");
        assertThat(AiErrors.reason(403, "")).contains("isn't allowed");
        // A plain bad request (e.g. an unreadable PDF) is left to the caller.
        assertThat(AiErrors.reason(400, "Could not process PDF")).isNull();
    }
}
