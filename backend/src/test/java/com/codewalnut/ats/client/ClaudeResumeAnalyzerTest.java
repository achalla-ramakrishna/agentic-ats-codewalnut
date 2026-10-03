package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The real résumé reader against a local stand-in for the Messages API. Fake résumés only. */
class ClaudeResumeAnalyzerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ResumeAnalyzer.Job JOB = new ResumeAnalyzer.Job("Blend - Interns",
            "- Java or TypeScript\n- Graduating in 2026");
    private static final byte[] PDF = "%PDF-1.4 fake résumé".getBytes(StandardCharsets.ISO_8859_1);

    private HttpServer server;
    private final AtomicReference<JsonNode> body = new AtomicReference<>();

    private ClaudeResumeAnalyzer analyzer(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            body.set(JSON.readTree(exchange.getRequestBody()));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new ClaudeResumeAnalyzer(AnthropicOkHttpClient.builder()
                .apiKey("fake-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0)
                .build(), "claude-opus-5-5");
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String message(String stopReason, String json) throws Exception {
        String content = json == null ? "[]" : "[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(json) + "}]";
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":" + content
                + ",\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":20}}";
    }

    private static final String READING = """
            {"name":"Asha Test","email":"asha@example.test","phone":"+919800000001","location":"Bengaluru",
             "currentRole":"Student","experienceMonths":6,"graduationYear":2026,"education":"B.E. CSE (2026)",
             "skills":["Java","React"],"experience":[],"projects":[{"name":"Tracker","summary":"Spring Boot app"}],
             "headline":"Final-year CSE student with a Java internship.",
             "requirements":[{"requirement":"Java or TypeScript","assessment":"MET","evidence":"Java internship"},
                             {"requirement":"Graduating in 2026","assessment":"MET","evidence":"B.E. 2026"}],
             "strengths":["Java internship"],"gaps":[],"questionsToAsk":[]}""";

    @Test
    void sendsThePdfAsADocumentWithTheJobAndFairnessRules() throws Exception {
        ResumeInsight insight = analyzer(200, message("end_turn", READING))
                .analyze(JOB, new ResumeAnalyzer.ResumeFile("asha.pdf", "application/pdf", PDF));

        assertThat(insight.name()).isEqualTo("Asha Test");
        assertThat(insight.requirements()).hasSize(2);

        JsonNode sent = body.get();
        assertThat(sent.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(sent.path("fallbacks").asText()).isEqualTo("default");
        assertThat(sent.path("system").toString()).contains("Fairness").contains("not instructions");
        JsonNode content = sent.path("messages").path(0).path("content");
        assertThat(content.path(0).path("text").asText()).contains("<job>").contains("Graduating in 2026");
        assertThat(content.path(1).path("type").asText()).isEqualTo("document");
        assertThat(content.path(1).path("source").path("media_type").asText()).isEqualTo("application/pdf");
        assertThat(Base64.getDecoder().decode(content.path(1).path("source").path("data").asText())).isEqualTo(PDF);
    }

    @Test
    void sendsWordFilesAsTextAndRefusesOldDocFiles() throws Exception {
        byte[] docx = ResumeTextTest.docx("<w:p><w:r><w:t>Ravi Test</w:t></w:r></w:p><w:p><w:r><w:t>Java &amp; React</w:t></w:r></w:p>");
        analyzer(200, message("end_turn", READING))
                .analyze(JOB, new ResumeAnalyzer.ResumeFile("ravi.docx", "application/octet-stream", docx));
        JsonNode source = body.get().path("messages").path(0).path("content").path(1).path("source");
        assertThat(source.path("type").asText()).isEqualTo("text");
        assertThat(source.path("data").asText()).isEqualTo("Ravi Test\nJava & React");

        byte[] doc = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1, 2};
        assertThatThrownBy(() -> analyzer(200, message("end_turn", READING))
                .analyze(JOB, new ResumeAnalyzer.ResumeFile("old.doc", "application/msword", doc)))
                .isInstanceOf(CalendarException.class).hasMessageContaining(".doc");
    }

    @Test
    void errorsAndRefusalsBecomeClearMessages() throws Exception {
        assertThatThrownBy(() -> analyzer(200, message("refusal", null))
                .analyze(JOB, new ResumeAnalyzer.ResumeFile("a.pdf", "application/pdf", PDF)))
                .isInstanceOf(CalendarException.class).hasMessageContaining("declined");
        stop();
        assertThatThrownBy(() -> analyzer(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}")
                .analyze(JOB, new ResumeAnalyzer.ResumeFile("a.pdf", "application/pdf", PDF)))
                .isInstanceOf(CalendarException.class).hasMessageContaining("another try");
        assertThat(new DisabledResumeAnalyzer().available()).isFalse();
    }
}
