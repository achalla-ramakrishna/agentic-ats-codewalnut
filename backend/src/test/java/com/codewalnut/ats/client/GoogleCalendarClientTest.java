package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/** The Google Calendar adapter's requests and error handling, against a mock Google. */
class GoogleCalendarClientTest {

    private static final ClientRegistration CALENDAR = CommonOAuth2Provider.GOOGLE
            .getBuilder(GoogleAccess.REGISTRATION_ID)
            .clientId("fake-id").clientSecret("fake-secret").scope(GoogleAccess.CALENDAR_SCOPE, GoogleAccess.GMAIL_SEND_SCOPE).build();
    private static final CalendarClient.Invite INVITE = new CalendarClient.Invite("CodeWalnut interview", "Hello",
            Instant.parse("2030-01-15T05:30:00Z"), Instant.parse("2030-01-15T06:15:00Z"), "Asia/Kolkata",
            List.of("candidate@example.com", "panel@codewalnut.test"));

    private final HttpSessionOAuth2AuthorizedClientRepository tokens = new HttpSessionOAuth2AuthorizedClientRepository();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final TestingAuthenticationToken staff = new TestingAuthenticationToken("staff@codewalnut.test", null, "ROLE_STAFF");
    private MockRestServiceServer google;
    private GoogleCalendarClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(GoogleCalendarClient.BASE_URL);
        google = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleCalendarClient(new GoogleAccess(new InMemoryClientRegistrationRepository(CALENDAR), tokens), builder.build());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        SecurityContextHolder.getContext().setAuthentication(staff);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void connect(Instant expiresAt) {
        connect(expiresAt, java.util.Set.of(GoogleAccess.CALENDAR_SCOPE, GoogleAccess.GMAIL_SEND_SCOPE));
    }

    private void connect(Instant expiresAt, java.util.Set<String> scopes) {
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "tok-123",
                Instant.now().minusSeconds(10), expiresAt, scopes);
        tokens.saveAuthorizedClient(new OAuth2AuthorizedClient(CALENDAR, staff.getName(), token), staff, request,
                new MockHttpServletResponse());
    }

    @Test
    void notConnectedUntilTheUserGrantsAccess() {
        assertThat(client.status()).isEqualTo(new CalendarClient.Status(true, false));
        assertThatThrownBy(() -> client.create(INVITE)).isInstanceOf(CalendarNotConnectedException.class);

        connect(Instant.now().plusSeconds(30));
        assertThat(client.status().connected()).as("about to expire counts as not connected").isFalse();

        connect(Instant.now().plusSeconds(3600), java.util.Set.of(GoogleAccess.GMAIL_SEND_SCOPE));
        assertThat(client.status().connected()).as("calendar permission unticked on the consent screen").isFalse();

        connect(Instant.now().plusSeconds(3600));
        assertThat(client.status().connected()).isTrue();
    }

    @Test
    void createsEventWithMeetLinkAndAsksGoogleToEmailAttendees() {
        connect(Instant.now().plusSeconds(3600));
        google.expect(requestTo(GoogleCalendarClient.BASE_URL
                        + "/calendars/primary/events?conferenceDataVersion=1&sendUpdates=all"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-123"))
                .andExpect(jsonPath("$.summary").value("CodeWalnut interview"))
                .andExpect(jsonPath("$.start.dateTime").value("2030-01-15T05:30:00Z"))
                .andExpect(jsonPath("$.start.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.attendees[0].email").value("candidate@example.com"))
                .andExpect(jsonPath("$.attendees[1].email").value("panel@codewalnut.test"))
                .andExpect(jsonPath("$.conferenceData.createRequest.conferenceSolutionKey.type").value("hangoutsMeet"))
                .andRespond(withSuccess("{\"id\":\"ev1\",\"htmlLink\":\"https://calendar.google.com/event?eid=1\","
                        + "\"hangoutLink\":\"https://meet.google.com/abc-defg-hij\"}", MediaType.APPLICATION_JSON));

        CalendarClient.Event event = client.create(INVITE);

        assertThat(event).isEqualTo(new CalendarClient.Event("ev1", "https://meet.google.com/abc-defg-hij",
                "https://calendar.google.com/event?eid=1"));
        google.verify();
    }

    @Test
    void readsMeetLinkFromConferenceEntryPointsToo() {
        assertThat(GoogleCalendarClient.meetLink(java.util.Map.of("conferenceData", java.util.Map.of("entryPoints",
                List.of(java.util.Map.of("entryPointType", "phone", "uri", "tel:1"),
                        java.util.Map.of("entryPointType", "video", "uri", "https://meet.google.com/x"))))))
                .isEqualTo("https://meet.google.com/x");
    }

    @Test
    void revokedTokenMeansReconnect() {
        connect(Instant.now().plusSeconds(3600));
        google.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.create(INVITE)).isInstanceOf(CalendarNotConnectedException.class);
        assertThat(client.status().connected()).isFalse();
    }

    @Test
    void apiDisabledOrDownIsAClearError() {
        connect(Instant.now().plusSeconds(3600));
        google.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.create(INVITE)).isInstanceOf(CalendarException.class)
                .hasMessageContaining("Google Calendar API");
    }

    @Test
    void cancelNotifiesAttendeesAndToleratesAlreadyDeletedEvents() {
        connect(Instant.now().plusSeconds(3600));
        google.expect(requestTo(GoogleCalendarClient.BASE_URL + "/calendars/primary/events/ev1?sendUpdates=all"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        google.expect(requestTo(GoogleCalendarClient.BASE_URL + "/calendars/primary/events/ev2?sendUpdates=all"))
                .andRespond(withStatus(HttpStatus.GONE));

        client.cancel("ev1");
        client.cancel("ev2");
        google.verify();
    }

    @Test
    void unavailableWithoutACalendarRegistration() {
        GoogleCalendarClient unconfigured = new GoogleCalendarClient(new GoogleAccess(null, tokens), RestClient.create());
        assertThat(unconfigured.status()).isEqualTo(new CalendarClient.Status(false, false));
        assertThatThrownBy(() -> unconfigured.create(INVITE)).isInstanceOf(CalendarException.class)
                .hasMessageContaining("isn't set up");
    }
}
