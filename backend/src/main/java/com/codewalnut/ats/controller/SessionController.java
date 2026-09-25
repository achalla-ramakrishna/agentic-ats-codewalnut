package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.SessionResponse;
import com.codewalnut.ats.security.SessionType;
import java.util.Arrays;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: tells the SPA whether to show the login page, the staff app or the candidate area. */
@RestController
public class SessionController {

    @GetMapping("/api/v1/auth/session")
    public SessionResponse session() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new SessionResponse(null);
        }
        SessionType type = Arrays.stream(SessionType.values())
                .filter(t -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(t.authority())))
                .findFirst()
                .orElse(null);
        return new SessionResponse(type);
    }
}
