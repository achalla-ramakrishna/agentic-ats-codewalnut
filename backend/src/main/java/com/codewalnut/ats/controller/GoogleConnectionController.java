package com.codewalnut.ats.controller;

import com.codewalnut.ats.client.GoogleAccess;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.GoogleConnectionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;

/** Staff connect their own Google account (Calendar + Gmail) for scheduling and email. */
@RestController
@RequiredArgsConstructor
public class GoogleConnectionController {

    static final String RETURN_TO = "ats.google.returnTo";

    /** redirectUri: what to register in Google Cloud Console for this connection. */
    public record GoogleStatusResponse(boolean available, boolean calendarConnected, boolean mailConnected, String redirectUri) {}

    private final GoogleConnectionService googleConnectionService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/google/status")
    public GoogleStatusResponse status() {
        currentUserService.require();
        GoogleConnectionService.Status status = googleConnectionService.status();
        String redirectUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/oauth2/callback/" + GoogleAccess.REGISTRATION_ID).toUriString();
        return new GoogleStatusResponse(status.available(), status.calendarConnected(), status.mailConnected(), redirectUri);
    }

    /** Full-page navigation from the SPA: remember where to come back to, then go to Google. */
    @GetMapping("/api/v1/google/connect")
    public RedirectView connect(@RequestParam(defaultValue = "/") String returnTo, HttpSession session) {
        currentUserService.require();
        GoogleConnectionService.Status status = googleConnectionService.status();
        String target = safeReturnTo(returnTo);
        if (status.fullyConnected()) {
            return new RedirectView(target, true);
        }
        session.setAttribute(RETURN_TO, target);
        return new RedirectView("/oauth2/authorization/" + GoogleAccess.REGISTRATION_ID, true);
    }

    /**
     * Spring Security handles Google's callback (with ?code) and then redirects here without
     * it; we send the user back to the screen they came from.
     */
    @GetMapping("/oauth2/callback/" + GoogleAccess.REGISTRATION_ID)
    public RedirectView connected(HttpSession session) {
        Object target = session.getAttribute(RETURN_TO);
        session.removeAttribute(RETURN_TO);
        return new RedirectView(target instanceof String s ? s : "/", true);
    }

    /** Only same-site paths, so the redirect can't be pointed at another site. */
    static String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.length() > 500 || !returnTo.startsWith("/") || returnTo.startsWith("//")
                || returnTo.contains("\\") || returnTo.chars().anyMatch(Character::isISOControl)) {
            return "/";
        }
        return returnTo;
    }
}
