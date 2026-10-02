package com.codewalnut.ats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * WhatsApp Business API (Meta Cloud API) settings. Everything is optional; see
 * docs/features/communication.md (MSG-21…) and ADR-0008.
 *
 * @param accessToken permanent token of the Meta system user that owns the WhatsApp number
 * @param phoneNumberId the WhatsApp Business phone number id (not the phone number itself)
 * @param templateName approved Utility template with three body variables: first name, role, message
 * @param templateLanguage the template's language code, e.g. "en"
 * @param verifyToken shared secret Meta echoes when the webhook is registered
 * @param appSecret Meta app secret, used to check the X-Hub-Signature-256 of every webhook call
 * @param defaultCountryCode added to 10-digit numbers (India: 91)
 */
@ConfigurationProperties(prefix = "ats.whatsapp")
public record WhatsAppProperties(
        String accessToken,
        String phoneNumberId,
        String templateName,
        String templateLanguage,
        String verifyToken,
        String appSecret,
        String defaultCountryCode) {

    public WhatsAppProperties {
        accessToken = trim(accessToken);
        phoneNumberId = trim(phoneNumberId);
        templateName = StringUtils.hasText(templateName) ? templateName.trim() : "candidate_message";
        templateLanguage = StringUtils.hasText(templateLanguage) ? templateLanguage.trim() : "en";
        verifyToken = trim(verifyToken);
        appSecret = trim(appSecret);
        defaultCountryCode = StringUtils.hasText(defaultCountryCode) ? defaultCountryCode.replaceAll("\\D", "") : "91";
    }

    public boolean apiEnabled() {
        return !accessToken.isEmpty() && !phoneNumberId.isEmpty();
    }

    public boolean webhookEnabled() {
        return !verifyToken.isEmpty() && !appSecret.isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
