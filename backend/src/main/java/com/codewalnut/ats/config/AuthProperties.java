package com.codewalnut.ats.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Staff sign-in settings.
 *
 * @param allowedDomains email domains allowed to sign in or be provisioned (e.g. codewalnut.com)
 * @param bootstrapAdmins emails auto-provisioned as ADMIN on first sign-in, so a fresh install
 *     has someone who can create the other users
 * @param devLoginEnabled enables the password-less dev login; only honoured in the dev profile
 * @param successUrl where the browser lands after a successful Google sign-in
 * @param failureUrl where the browser lands after a rejected Google sign-in
 */
@ConfigurationProperties(prefix = "ats.auth")
public record AuthProperties(
        List<String> allowedDomains,
        List<String> bootstrapAdmins,
        boolean devLoginEnabled,
        String successUrl,
        String failureUrl) {

    public AuthProperties {
        allowedDomains = normalize(allowedDomains);
        bootstrapAdmins = normalize(bootstrapAdmins);
        successUrl = successUrl == null ? "/" : successUrl;
        failureUrl = failureUrl == null ? "/login?error" : failureUrl;
    }

    public boolean isAllowedEmail(String email) {
        int at = email.lastIndexOf('@');
        return at > 0 && allowedDomains.contains(email.substring(at + 1));
    }

    public boolean isBootstrapAdmin(String email) {
        return bootstrapAdmins.contains(email);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .toList();
    }
}
