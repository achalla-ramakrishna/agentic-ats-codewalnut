package com.codewalnut.ats.security;

import com.codewalnut.ats.service.SignInService;
import com.codewalnut.ats.service.SignInService.SignedIn;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Maps a Google sign-in onto a session. Any verified Google account may sign in: staff-domain
 * accounts must be provisioned staff; all others (e.g. personal Gmail) become candidates.
 * Unverified emails and rejected staff sign-ins fail here, before a session is created.
 */
@Component
@RequiredArgsConstructor
public class GoogleOidcUserService extends OidcUserService {

    private final SignInService signInService;

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);
        if (!Boolean.TRUE.equals(google.getEmailVerified()) || google.getEmail() == null) {
            throw reject("Google account email is not verified");
        }
        SignedIn signedIn;
        try {
            signedIn = signInService.signIn(google.getEmail(), google.getFullName(), google.getSubject());
        } catch (LoginRejectedException ex) {
            throw reject(ex.getMessage());
        }
        return new DefaultOidcUser(signedIn.authorities(), google.getIdToken(), google.getUserInfo(), "email");
    }

    private static OAuth2AuthenticationException reject(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied", message, null), message);
    }
}
