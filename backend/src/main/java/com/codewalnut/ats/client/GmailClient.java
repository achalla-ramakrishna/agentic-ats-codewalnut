package com.codewalnut.ats.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Gmail API {@code users.messages.send} as the signed-in staff member (scope {@code gmail.send} only). */
@Slf4j
public class GmailClient implements MailClient {

    public static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1";

    private final GoogleAccess google;
    private final RestClient rest;

    public GmailClient(GoogleAccess google, RestClient rest) {
        this.google = google;
        this.rest = rest;
    }

    @Override
    public Status status() {
        return new Status(google.available(), google.token(GoogleAccess.GMAIL_SEND_SCOPE) != null);
    }

    @Override
    public String send(Email email) {
        if (!google.available()) {
            throw new CalendarException("Email isn't set up for this app yet. Ask an admin.");
        }
        String token = google.token(GoogleAccess.GMAIL_SEND_SCOPE);
        if (token == null) {
            throw new CalendarNotConnectedException();
        }
        String raw = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MimeMessages.plainText(email).getBytes(StandardCharsets.UTF_8));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> sent = rest.post()
                    .uri("/users/me/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(Map.of("raw", raw))
                    .retrieve()
                    .body(Map.class);
            return sent == null ? null : (String) sent.get("id");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            GoogleErrors.Details details = GoogleErrors.of(e);
            log.warn("Gmail send failed with HTTP {} ({}): {}", status, details.reason(), details.message());
            if (status == 401 || details.kind() == GoogleErrors.Kind.SCOPE_MISSING) {
                // Token revoked, or "Send email on your behalf" was unticked: connect again.
                google.forget();
                throw new CalendarNotConnectedException();
            }
            if (details.kind() == GoogleErrors.Kind.API_DISABLED) {
                throw new CalendarException("The Gmail API isn't enabled for this app. An admin needs to enable it in "
                        + "Google Cloud Console (APIs & Services → Library → Gmail API), then try again in a few "
                        + "minutes. Nothing was sent.");
            }
            if (status == 403) {
                throw new CalendarException("Gmail refused to send"
                        + (details.message() != null ? ": " + details.message() : ".") + " Nothing was sent.");
            }
            throw new CalendarException("Gmail had a problem (HTTP " + status + "). Nothing was sent; please try again.");
        } catch (RestClientException e) {
            log.warn("Gmail send failed: {}", e.getMessage());
            throw new CalendarException("Couldn't reach Gmail. Nothing was sent; please try again.");
        }
    }
}
