package com.codewalnut.ats.client;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The signed-in staff member's Google access token for Calendar and Gmail, granted through the
 * {@value #REGISTRATION_ID} OAuth client and kept in their session only (ADR-0005, ADR-0006).
 */
public class GoogleAccess {

    /** Kept as "google-calendar" so the registered redirect URI stays the same. */
    public static final String REGISTRATION_ID = "google-calendar";
    public static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events";
    public static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";

    private final ClientRegistrationRepository registrations;
    private final OAuth2AuthorizedClientRepository authorizedClients;

    public GoogleAccess(ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository authorizedClients) {
        this.registrations = registrations;
        this.authorizedClients = authorizedClients;
    }

    public boolean available() {
        return registrations != null && registrations.findByRegistrationId(REGISTRATION_ID) != null;
    }

    /** The token if the user granted {@code scope} and it isn't about to expire; otherwise null. */
    public String token(String scope) {
        if (!available()) {
            return null;
        }
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
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
        // Google lets people untick individual permissions on the consent screen.
        if (!token.getScopes().isEmpty() && !token.getScopes().contains(scope)) {
            return null;
        }
        return token.getTokenValue();
    }

    /** Google said the token is no longer valid: drop it so the UI asks to reconnect. */
    public void forget() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs && auth != null) {
            authorizedClients.removeAuthorizedClient(REGISTRATION_ID, auth, attrs.getRequest(), attrs.getResponse());
        }
    }
}
