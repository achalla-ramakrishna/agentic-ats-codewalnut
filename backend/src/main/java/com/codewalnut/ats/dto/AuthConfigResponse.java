package com.codewalnut.ats.dto;

import java.util.List;

/** What the login screen should offer. devUsers is empty unless the dev login is on. */
public record AuthConfigResponse(boolean googleEnabled, boolean devLoginEnabled, List<DevUser> devUsers) {

    public record DevUser(String email, String label) {}
}
