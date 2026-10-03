package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The real question drafter against a local stand-in for the Messages API. */
class ClaudeAssessmentDrafterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void asksForStructuredQuestionsAndSkipsExistingOnes() throws Exception {
        AtomicReference<JsonNode> body = new AtomicReference<>();
        String draft = "{\"questions\":[{\"kind\":\"SINGLE_CHOICE\",\"prompt\":\"Pick one\",\"code\":\"\","
                + "\"options\":[\"a\",\"b\",\"c\",\"d\"],\"correctOptions\":[2],\"acceptedAnswers\":[],\"explanation\":\"c\",\"points\":1}]}";
        String response = "{\"id\":\"m\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
                + "\"content\":[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(draft) + "}],"
                + "\"stop_reason\":\"end_turn\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            body.set(JSON.readTree(exchange.getRequestBody()));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        ClaudeAssessmentDrafter drafter = new ClaudeAssessmentDrafter(AnthropicOkHttpClient.builder()
                .apiKey("fake-key").baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).maxRetries(0).build(),
                "claude-opus-5-5");

        AssessmentDraft result = drafter.draft(new AssessmentDrafter.Request("JAVA", "Java basics", "collections",
                "Fresher", 5, List.of("What is a HashMap?")));

        assertThat(result.questions()).singleElement().satisfies(q -> assertThat(q.correctOptions()).containsExactly(2));
        assertThat(body.get().path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        String user = body.get().path("messages").path(0).path("content").toString();
        assertThat(user).contains("Category: JAVA").contains("collections").contains("What is a HashMap?").contains("Write 5 questions");
        assertThat(body.get().path("system").toString()).contains("one unambiguous correct answer");
    }

    @Test
    void theStarterBankHasValidAnswers() {
        for (List<AssessmentDraft.Question> bank : List.of(SampleAssessmentDrafter.APTITUDE, SampleAssessmentDrafter.JAVA,
                SampleAssessmentDrafter.PYTHON)) {
            for (AssessmentDraft.Question q : bank) {
                if (q.kind().equals("SHORT_ANSWER")) {
                    assertThat(q.acceptedAnswers()).isNotEmpty();
                } else {
                    assertThat(q.correctOptions()).isNotEmpty().allMatch(i -> i >= 0 && i < q.options().size());
                }
            }
        }
    }
}
