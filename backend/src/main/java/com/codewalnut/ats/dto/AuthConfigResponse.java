package com.codewalnut.ats.dto;

import java.util.List;

/**
 * What the login screen should offer. devUsers is empty unless the dev login is on.
 * googleRedirectUri is the URI to register in Google Cloud Console (shown to help setup).
 */
public record AuthConfigResponse(
        boolean googleEnabled,
        String googleRedirectUri,
        boolean devLoginEnabled,
        boolean accessCodeRequired,
        List<DevUser> devUsers) {

    public record DevUser(String email, String label) {}
}
