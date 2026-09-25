package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Exercises the real browser contract over HTTP (no MockMvc test helpers): the SPA reads the
 * XSRF-TOKEN cookie, echoes it in X-XSRF-TOKEN, and rides the session cookie afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpaSessionFlowTest {

    @LocalServerPort
    private int port;

    @Test
    void csrfCookieDevLoginAndSessionWorkEndToEnd() throws Exception {
        CookieManager cookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String base = "http://localhost:" + port;

        HttpResponse<String> config = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/config")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(config.statusCode()).isEqualTo(200);
        String xsrf = cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN cookie not set"));

        HttpRequest.Builder login = HttpRequest.newBuilder(URI.create(base + "/api/v1/auth/dev-login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"approver@codewalnut.test\"}"));

        HttpResponse<String> withoutHeader = client.send(login.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(withoutHeader.statusCode()).isEqualTo(403);

        HttpResponse<String> loggedIn = client.send(login.header("X-XSRF-TOKEN", xsrf).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(loggedIn.statusCode()).isEqualTo(204);

        HttpResponse<String> me = client.send(
                HttpRequest.newBuilder(URI.create(base + "/api/v1/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"approver@codewalnut.test\"").contains("\"approvals\"");
        assertThat(cookies.getCookieStore().getCookies()).anyMatch(c -> c.getName().equals("JSESSIONID"));
    }
}
