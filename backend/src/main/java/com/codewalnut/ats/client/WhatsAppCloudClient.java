package com.codewalnut.ats.client;

import com.codewalnut.ats.config.WhatsAppProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Meta WhatsApp Cloud API: {@code POST /{phone-number-id}/messages}. */
@Slf4j
public class WhatsAppCloudClient implements WhatsAppClient {

    public static final String BASE_URL = "https://graph.facebook.com/v21.0";
    /** Template variables can't contain line breaks or long runs of spaces, and are length-limited. */
    static final int MAX_TEMPLATE_TEXT = 900;

    private final WhatsAppProperties properties;
    private final RestClient rest;

    public WhatsAppCloudClient(WhatsAppProperties properties, RestClient rest) {
        this.properties = properties;
        this.rest = rest;
    }

    @Override
    public boolean apiEnabled() {
        return properties.apiEnabled();
    }

    @Override
    public String send(Outgoing message) {
        if (!apiEnabled()) {
            throw new CalendarException("WhatsApp Business API isn't set up.");
        }
        Map<String, Object> body = message.freeForm()
                ? Map.of("messaging_product", "whatsapp", "to", message.to(), "type", "text",
                        "text", Map.of("preview_url", true, "body", message.text()))
                : Map.of("messaging_product", "whatsapp", "to", message.to(), "type", "template",
                        "template", Map.of(
                                "name", properties.templateName(),
                                "language", Map.of("code", properties.templateLanguage()),
                                "components", List.of(Map.of("type", "body", "parameters", List.of(
                                        param(message.firstName()), param(message.jobTitle()),
                                        param(templateText(message.text())))))));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> sent = rest.post()
                    .uri("/{id}/messages", properties.phoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object messages = sent == null ? null : sent.get("messages");
            if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                return (String) first.get("id");
            }
            throw new CalendarException("WhatsApp didn't confirm the message. Nothing was sent; please try again.");
        } catch (RestClientResponseException e) {
            log.warn("WhatsApp send failed with HTTP {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new CalendarException("WhatsApp refused the message (HTTP " + e.getStatusCode().value()
                    + "). Check the WhatsApp Business setup and that the template is approved.");
        } catch (RestClientException e) {
            log.warn("WhatsApp send failed: {}", e.getMessage());
            throw new CalendarException("Couldn't reach WhatsApp. Please try again.");
        }
    }

    private static Map<String, Object> param(String text) {
        return Map.of("type", "text", "text", text == null || text.isBlank() ? "-" : text);
    }

    /** One line, no runs of spaces, within WhatsApp's limit. */
    static String templateText(String text) {
        String flat = text.replaceAll("\\s*\\R+\\s*", " · ").replaceAll("[\\t ]{2,}", " ").strip();
        return flat.length() > MAX_TEMPLATE_TEXT ? flat.substring(0, MAX_TEMPLATE_TEXT - 1) + "…" : flat;
    }
}
