package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Ask ATS tool loop against a local stand-in for the Messages API (no network, no key). */
class ClaudeAskClientTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Deque<String> replies = new ConcurrentLinkedDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;
    private ClaudeAskClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requests.add(json.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] body = replies.removeFirst().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new ClaudeAskClient(AnthropicOkHttpClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("test-key")
                .maxRetries(0)
                .build(), "claude-opus-5-5");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String message(String stop, String content) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","stop_reason":"%s",
                 "stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":5},"content":[%s]}""".formatted(stop, content);
    }

    private static final List<AskClient.ToolSpec> TOOLS = List.of(new AskClient.ToolSpec("search_candidates", "Find candidates",
            Map.of("stage", Map.of("type", "string")), List.of()));

    @Test
    void runsToolsInOneRoundAndReturnsTheFinalAnswer() throws Exception {
        replies.add(message("tool_use", """
                {"type":"text","text":"Let me check."},
                {"type":"tool_use","id":"tu_1","name":"search_candidates","input":{"stage":"SHORTLISTED"}},
                {"type":"tool_use","id":"tu_2","name":"search_candidates","input":{"stage":"SELECTED"}}"""));
        replies.add(message("end_turn", "{\"type\":\"text\",\"text\":\"**Asha** is shortlisted.\"}"));
        List<String> calls = new ArrayList<>();

        AskClient.Answer answer = client.answer(
                List.of(new AskClient.Turn(true, "Earlier question"), new AskClient.Turn(false, "Earlier answer"),
                        new AskClient.Turn(true, "Who is shortlisted?")),
                "GUIDE TEXT", TOOLS, (name, input) -> {
                    calls.add(name + ":" + input.get("stage"));
                    if ("SELECTED".equals(input.get("stage"))) {
                        throw new IllegalArgumentException("Not allowed: this person can't see candidates.");
                    }
                    return "{\"candidates\":[{\"name\":\"Asha\"}]}";
                });

        assertThat(answer.text()).isEqualTo("**Asha** is shortlisted.");
        assertThat(answer.toolsUsed()).containsExactly("search_candidates", "search_candidates");
        assertThat(calls).containsExactly("search_candidates:SHORTLISTED", "search_candidates:SELECTED");

        JsonNode first = requests.get(0);
        assertThat(first.at("/model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(first.at("/system/0/text").asText()).contains("Ask ATS", "GUIDE TEXT");
        assertThat(first.at("/system/0/cache_control/type").asText()).isEqualTo("ephemeral");
        assertThat(first.at("/tools/0/name").asText()).isEqualTo("search_candidates");
        assertThat(first.at("/tools/0/input_schema/properties/stage/type").asText()).isEqualTo("string");
        assertThat(first.at("/messages").size()).isEqualTo(3);
        assertThat(first.at("/fallbacks").asText()).isEqualTo("default");

        // Second request: the assistant turn echoed, then both results in one user message.
        JsonNode second = requests.get(1);
        JsonNode msgs = second.at("/messages");
        assertThat(msgs.size()).isEqualTo(5);
        assertThat(msgs.get(3).at("/role").asText()).isEqualTo("assistant");
        assertThat(msgs.get(3).at("/content/1/type").asText()).isEqualTo("tool_use");
        JsonNode results = msgs.get(4).at("/content");
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).at("/tool_use_id").asText()).isEqualTo("tu_1");
        assertThat(results.get(0).at("/content").asText()).contains("Asha");
        assertThat(results.get(1).at("/is_error").asBoolean()).isTrue();
        assertThat(results.get(1).at("/content").asText()).contains("Not allowed");
    }

    @Test
    void aRefusalIsReportedPlainly() {
        replies.add(message("refusal", "{\"type\":\"text\",\"text\":\"\"}"));
        assertThatThrownBy(() -> client.answer(List.of(new AskClient.Turn(true, "x")), "", TOOLS, (n, i) -> "{}"))
                .isInstanceOf(CalendarException.class)
                .hasMessageContaining("declined");
    }

    @Test
    void stopsAfterTooManyLookups() {
        for (int i = 0; i < ClaudeAskClient.MAX_ROUNDS; i++) {
            replies.add(message("tool_use", "{\"type\":\"tool_use\",\"id\":\"tu_" + i + "\",\"name\":\"search_candidates\",\"input\":{}}"));
        }
        assertThatThrownBy(() -> client.answer(List.of(new AskClient.Turn(true, "x")), "", TOOLS, (n, i) -> "{}"))
                .hasMessageContaining("too many lookups");
        assertThat(requests).hasSize(ClaudeAskClient.MAX_ROUNDS);
    }
}
