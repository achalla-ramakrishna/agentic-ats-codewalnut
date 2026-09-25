package com.codewalnut.ats.service;

import com.codewalnut.ats.config.AuthProperties;
import com.codewalnut.ats.security.SessionType;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Routes a verified email to the right kind of session. Staff-domain emails (e.g.
 * codewalnut.com) must be provisioned staff — they never fall through to a candidate account.
 * Every other email (e.g. a personal Gmail) signs in as a candidate.
 */
@Service
@RequiredArgsConstructor
public class SignInService {

    private final AuthProperties authProperties;
    private final AuthService authService;
    private final CandidateAuthService candidateAuthService;

    public record SignedIn(String email, SessionType type, List<GrantedAuthority> authorities) {}

    public SignedIn signIn(String rawEmail, String name, String googleSubject) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (authProperties.isAllowedEmail(email)) {
            var user = authService.completeLogin(email, name, googleSubject);
            return new SignedIn(user.getEmail(), SessionType.STAFF, AuthService.authoritiesFor(user));
        }
        var account = candidateAuthService.completeLogin(email, name, googleSubject);
        return new SignedIn(account.getEmail(), SessionType.CANDIDATE, CandidateAuthService.authorities());
    }
}
