package com.codewalnut.ats.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google Calendar API v3 on the staff member's own primary calendar, using the access token
 * they granted (see {@link GoogleAccess}). Events get a Google Meet link, and
 * {@code sendUpdates=all} makes Google email the invitation (and later the cancellation) to
 * every attendee.
 */
@Slf4j
public class GoogleCalendarClient implements CalendarClient {

    public static final String BASE_URL = "https://www.googleapis.com/calendar/v3";

    private final GoogleAccess google;
    private final RestClient rest;

    public GoogleCalendarClient(GoogleAccess google, RestClient rest) {
        this.google = google;
        this.rest = rest;
    }

    @Override
    public Status status() {
        return new Status(google.available(), google.token(GoogleAccess.CALENDAR_SCOPE) != null);
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
        GoogleErrors.Details details = GoogleErrors.of(e);
        log.warn("Google Calendar {} failed with HTTP {} ({}): {}", action, status, details.reason(), details.message());
        if (status == 401 || details.kind() == GoogleErrors.Kind.SCOPE_MISSING) {
            google.forget();
            return new CalendarNotConnectedException();
        }
        if (details.kind() == GoogleErrors.Kind.API_DISABLED) {
            return new CalendarException("The Google Calendar API isn't enabled for this app. An admin needs to enable it "
                    + "in Google Cloud Console (APIs & Services → Library → Google Calendar API), then try again in a "
                    + "few minutes. Nothing was sent.");
        }
        if (status == 403) {
            return new CalendarException("Google Calendar refused the request"
                    + (details.message() != null ? ": " + details.message() : ".") + " Nothing was sent.");
        }
        return new CalendarException("Google Calendar had a problem (HTTP " + status + "). Please try again.");
    }

    private String requireToken() {
        if (!google.available()) {
            throw new CalendarException("Google Calendar isn't set up for this app yet. Ask an admin.");
        }
        String token = google.token(GoogleAccess.CALENDAR_SCOPE);
        if (token == null) {
            throw new CalendarNotConnectedException();
        }
        return token;
    }
}
