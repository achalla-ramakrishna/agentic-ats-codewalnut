package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** ADR-0016: the Judge0 adapter sends source and inputs (never expected output) and maps statuses. */
class Judge0CodeRunnerTest {

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void runsABatchAndMapsEachResult() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://judge0.test").defaultHeader("X-Auth-Token", "fake-token");
        MockRestServiceServer judge = MockRestServiceServer.bindTo(builder).build();
        judge.expect(requestTo("http://judge0.test/submissions/batch?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Auth-Token", "fake-token"))
                .andExpect(jsonPath("$.submissions.length()").value(3))
                .andExpect(jsonPath("$.submissions[0].language_id").value(71))
                .andExpect(jsonPath("$.submissions[0].source_code").value(b64("print(input())")))
                .andExpect(jsonPath("$.submissions[1].stdin").value(b64("2\n")))
                .andExpect(jsonPath("$.submissions[0].cpu_time_limit").value(6.0))
                .andExpect(jsonPath("$.submissions[0].memory_limit").value(256 * 1024))
                .andExpect(jsonPath("$.submissions[0].expected_output").doesNotExist())
                .andRespond(withSuccess("[{\"token\":\"a\"},{\"token\":\"b\"},{\"token\":\"c\"}]", MediaType.APPLICATION_JSON));
        // First poll: still running; second poll: done.
        judge.expect(requestTo(Matchers.startsWith("http://judge0.test/submissions/batch?tokens=a%2Cb%2Cc")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"submissions\":[{\"token\":\"a\",\"status\":{\"id\":2}},{\"token\":\"b\",\"status\":{\"id\":1}},"
                        + "{\"token\":\"c\",\"status\":{\"id\":2}}]}", MediaType.APPLICATION_JSON));
        judge.expect(requestTo(Matchers.startsWith("http://judge0.test/submissions/batch?tokens=a%2Cb%2Cc")))
                .andRespond(withSuccess("{\"submissions\":["
                        + "{\"token\":\"a\",\"status\":{\"id\":3},\"stdout\":\"" + b64("1\n") + "\",\"time\":\"0.02\",\"memory\":3100},"
                        + "{\"token\":\"b\",\"status\":{\"id\":5},\"time\":\"6.0\"},"
                        + "{\"token\":\"c\",\"status\":{\"id\":11},\"stderr\":\"" + b64("Traceback: ZeroDivisionError") + "\"}]}",
                        MediaType.APPLICATION_JSON));
        Judge0CodeRunner runner = new Judge0CodeRunner(builder.build(), Map.of("python", 71));

        List<CodeRunner.Result> results = runner.run("python", "print(input())", List.of("1\n", "2\n", "3\n"), new CodeRunner.Limits(6, 256));

        assertThat(results).extracting(CodeRunner.Result::status)
                .containsExactly(CodeRunner.Status.OK, CodeRunner.Status.TIME_LIMIT, CodeRunner.Status.RUNTIME_ERROR);
        assertThat(results.get(0).stdout()).isEqualTo("1\n");
        assertThat(results.get(0).memoryKb()).isEqualTo(3100);
        assertThat(results.get(2).stderr()).contains("ZeroDivisionError");
        judge.verify();
    }

    @Test
    void anUnreachableSandboxIsReportedAsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://judge0.test");
        MockRestServiceServer judge = MockRestServiceServer.bindTo(builder).build();
        judge.expect(requestTo("http://judge0.test/submissions/batch?base64_encoded=true")).andRespond(withServerError());
        Judge0CodeRunner runner = new Judge0CodeRunner(builder.build(), Map.of("java", 62));

        assertThatThrownBy(() -> runner.run("java", "class Main {}", List.of(""), new CodeRunner.Limits(2, 256)))
                .isInstanceOf(CodeRunner.UnavailableException.class);
        assertThatThrownBy(() -> runner.run("rust", "fn main(){}", List.of(""), new CodeRunner.Limits(2, 256)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compileErrorsCarryTheCompilerMessage() {
        var r = Judge0CodeRunner.result(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
                "status", Map.of("id", 6), "compile_output", b64("Main.java:3: error: ';' expected"))));
        assertThat(r.status()).isEqualTo(CodeRunner.Status.COMPILE_ERROR);
        assertThat(r.compileOutput()).contains("';' expected");
    }
}
