package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Sending through the Gmail API, against a mock Google. */
class GmailClientTest {

    private static final ClientRegistration GOOGLE = CommonOAuth2Provider.GOOGLE
            .getBuilder(GoogleAccess.REGISTRATION_ID).clientId("fake-id").clientSecret("fake-secret")
            .scope(GoogleAccess.CALENDAR_SCOPE, GoogleAccess.GMAIL_SEND_SCOPE).build();

    private final HttpSessionOAuth2AuthorizedClientRepository tokens = new HttpSessionOAuth2AuthorizedClientRepository();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final TestingAuthenticationToken staff = new TestingAuthenticationToken("staff@codewalnut.test", null, "ROLE_STAFF");
    private MockRestServiceServer google;
    private GmailClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GmailClient.BASE_URL);
        google = MockRestServiceServer.bindTo(builder).build();
        client = new GmailClient(new GoogleAccess(new InMemoryClientRegistrationRepository(GOOGLE), tokens), builder.build());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        SecurityContextHolder.getContext().setAuthentication(staff);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void connect(Set<String> scopes) {
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "tok-9",
                Instant.now(), Instant.now().plusSeconds(3600), scopes);
        tokens.saveAuthorizedClient(new OAuth2AuthorizedClient(GOOGLE, staff.getName(), token), staff, request,
                new MockHttpServletResponse());
    }

    @Test
    void sendsAPlainTextMessageAsTheStaffMember() {
        connect(Set.of(GoogleAccess.CALENDAR_SCOPE, GoogleAccess.GMAIL_SEND_SCOPE));
        AtomicReference<String> raw = new AtomicReference<>();
        google.expect(requestTo(GmailClient.BASE_URL + "/users/me/messages/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-9"))
                .andExpect(r -> raw.set(JsonPath.read(((MockClientHttpRequest) r).getBodyAsString(), "$.raw")))
                .andRespond(withSuccess("{\"id\":\"msg-1\"}", MediaType.APPLICATION_JSON));

        String id = client.send(new MailClient.Email("meera@example.com", "Interview – next steps ✓", "Hi Meera,\nSee you."));

        assertThat(id).isEqualTo("msg-1");
        String mime = new String(Base64.getUrlDecoder().decode(raw.get()), StandardCharsets.UTF_8);
        assertThat(mime).startsWith("To: meera@example.com\r\n").contains("Content-Type: text/plain; charset=UTF-8");
        google.verify();
    }

    @Test
    void notConnectedOrGmailPermissionUntickedMeansConnect() {
        assertThat(client.status()).isEqualTo(new MailClient.Status(true, false));
        connect(Set.of(GoogleAccess.CALENDAR_SCOPE));
        assertThat(client.status().connected()).isFalse();
        assertThatThrownBy(() -> client.send(new MailClient.Email("a@example.com", "s", "b")))
                .isInstanceOf(CalendarNotConnectedException.class);
    }

    @Test
    void revokedTokenAndRefusalsAreClear() {
        connect(Set.of(GoogleAccess.GMAIL_SEND_SCOPE));
        google.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        google.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.send(new MailClient.Email("a@example.com", "s", "b")))
                .isInstanceOf(CalendarException.class).hasMessageContaining("Nothing was sent");
        assertThatThrownBy(() -> client.send(new MailClient.Email("a@example.com", "s", "b")))
                .isInstanceOf(CalendarNotConnectedException.class);
        assertThat(client.status().connected()).isFalse();
    }
}
