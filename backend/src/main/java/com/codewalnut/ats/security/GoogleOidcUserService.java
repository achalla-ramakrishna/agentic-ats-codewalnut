package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Maps a Google sign-in onto a provisioned AppUser. Unverified emails, other domains and
 * unknown or inactive users are rejected here, before a session is created.
 */
@Component
@RequiredArgsConstructor
public class GoogleOidcUserService extends OidcUserService {

    private final AuthService authService;

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);
        if (!Boolean.TRUE.equals(google.getEmailVerified()) || google.getEmail() == null) {
            throw reject("Google account email is not verified");
        }
        AppUser user;
        try {
            user = authService.completeLogin(google.getEmail(), google.getFullName(), google.getSubject());
        } catch (LoginRejectedException ex) {
            throw reject(ex.getMessage());
        }
        return new DefaultOidcUser(
                AuthService.authoritiesFor(user), google.getIdToken(), google.getUserInfo(), "email");
    }

    private static OAuth2AuthenticationException reject(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied", message, null), message);
    }
}
