package com.codewalnut.ats.dto;

import com.codewalnut.ats.security.SignInRefusal;
import java.util.List;

/**
 * What the login screen should offer. devUsers is empty unless the dev login is on.
 * googleRedirectUri is the URI to register in Google Cloud Console (shown to help setup).
 * signInRefused: why this browser's last Google sign-in was refused (shown once), or null.
 */
public record AuthConfigResponse(
        boolean googleEnabled,
        String googleRedirectUri,
        boolean devLoginEnabled,
        boolean accessCodeRequired,
        List<DevUser> devUsers,
        SignInRefusal signInRefused) {

    public record DevUser(String email, String label) {}
}
