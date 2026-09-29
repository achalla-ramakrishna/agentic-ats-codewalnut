package com.codewalnut.ats.client;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Google Calendar API v3 on the staff member's own primary calendar, using the access token
 * they granted through the {@value #REGISTRATION_ID} OAuth client (scope
 * {@code calendar.events} only). Events get a Google Meet link, and {@code sendUpdates=all}
 * makes Google email the invitation (and later the cancellation) to every attendee.
 */
@Slf4j
public class GoogleCalendarClient implements CalendarClient {

    public static final String REGISTRATION_ID = "google-calendar";
    public static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    public static final String BASE_URL = "https://www.googleapis.com/calendar/v3";

    private final ClientRegistrationRepository registrations;
    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final RestClient rest;

    public GoogleCalendarClient(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository authorizedClients, RestClient rest) {
        this.registrations = registrations;
        this.authorizedClients = authorizedClients;
        this.rest = rest;
    }

    @Override
    public Status status() {
        boolean available = registrations != null && registrations.findByRegistrationId(REGISTRATION_ID) != null;
        return new Status(available, available && accessToken() != null);
    }

    @Override
    public Event create(Invite invite) {
        String token = requireToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", invite.title());
        body.put("description", invite.description());
        body.put("start", Map.of("dateTime", invite.start().toString(), "timeZone", invite.timeZone()));
        body.put("end", Map.of("dateTime", invite.end().toString(), "timeZone", invite.timeZone()));
        body.put("attendees", invite.attendees().stream().map(email -> Map.of("email", email)).toList());
        body.put("guestsCanModify", false);
        body.put("guestsCanInviteOthers", false);
        body.put("conferenceData", Map.of("createRequest", Map.of(
                "requestId", UUID.randomUUID().toString(),
                "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> created = rest.post()
                    .uri("/calendars/primary/events?conferenceDataVersion=1&sendUpdates=all")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (created == null || created.get("id") == null) {
                throw new CalendarException("Google Calendar didn't return the event. Nothing was sent; please try again.");
            }
            return new Event((String) created.get("id"), meetLink(created), (String) created.get("htmlLink"));
        } catch (RestClientResponseException e) {
            throw translate(e, "create");
        } catch (RestClientException e) {
            log.warn("Google Calendar create failed: {}", e.getMessage());
            throw new CalendarException("Couldn't reach Google Calendar. Nothing was sent; please try again.");
        }
    }

    @Override
    public void cancel(String eventId) {
        String token = requireToken();
        try {
            rest.delete()
                    .uri("/calendars/primary/events/{id}?sendUpdates=all", eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND || status == HttpStatus.GONE) {
                return;
            }
            throw translate(e, "cancel");
        } catch (RestClientException e) {
            log.warn("Google Calendar cancel failed: {}", e.getMessage());
            throw new CalendarException("Couldn't reach Google Calendar. The interview is still on; please try again.");
        }
    }

    @SuppressWarnings("unchecked")
    static String meetLink(Map<String, Object> event) {
        Object hangout = event.get("hangoutLink");
        if (hangout instanceof String link && !link.isBlank()) {
            return link;
        }
        if (event.get("conferenceData") instanceof Map<?, ?> conference
                && conference.get("entryPoints") instanceof List<?> entryPoints) {
            for (Object entry : entryPoints) {
                if (entry instanceof Map<?, ?> point && "video".equals(point.get("entryPointType"))) {
                    return (String) point.get("uri");
                }
            }
        }
        return null;
    }

    private RuntimeException translate(RestClientResponseException e, String action) {
        int status = e.getStatusCode().value();
        log.warn("Google Calendar {} failed with HTTP {}", action, status);
        if (status == 401) {
            forgetToken();
            return new CalendarNotConnectedException();
        }
        if (status == 403) {
            return new CalendarException("Google Calendar refused the request. Check that the Google Calendar API is "
                    + "enabled for this app and that you allowed calendar access.");
        }
        return new CalendarException("Google Calendar had a problem (HTTP " + status + "). Please try again.");
    }

    private String requireToken() {
        if (!status().available()) {
            throw new CalendarException("Google Calendar isn't set up for this app yet. Ask an admin.");
        }
        String token = accessToken();
        if (token == null) {
            throw new CalendarNotConnectedException();
        }
        return token;
    }

    /** The current user's token, or null if they haven't connected or it is about to expire. */
    private String accessToken() {
        HttpServletRequest request = currentRequest();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (request == null || auth == null) {
            return null;
        }
        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(REGISTRATION_ID, auth, request);
        if (client == null) {
            return null;
        }
        OAuth2AccessToken token = client.getAccessToken();
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now().plusSeconds(60))) {
            return null;
        }
        return token.getTokenValue();
    }

    private void forgetToken() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (attrs != null && auth != null) {
            authorizedClients.removeAuthorizedClient(REGISTRATION_ID, auth, attrs.getRequest(), attrs.getResponse());
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
    }
}
