package com.codewalnut.ats.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads Google's machine-readable reason from an API error, so a 403 can say *why*: the API is
 * switched off in the Cloud project, or the user didn't grant the permission.
 */
final class GoogleErrors {

    enum Kind { API_DISABLED, SCOPE_MISSING, OTHER }

    record Details(Kind kind, String reason, String message) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private GoogleErrors() {}

    static Details of(RestClientResponseException e) {
        String reason = null;
        String status = null;
        String message = null;
        try {
            JsonNode error = JSON.readTree(e.getResponseBodyAsString()).path("error");
            reason = text(error.path("errors").path(0).path("reason"));
            status = text(error.path("status"));
            message = text(error.path("message"));
            for (JsonNode detail : error.path("details")) {
                String detailReason = text(detail.path("reason"));
                if (detailReason != null) {
                    reason = reason == null ? detailReason : reason;
                    if ("SERVICE_DISABLED".equals(detailReason) || "ACCESS_TOKEN_SCOPE_INSUFFICIENT".equals(detailReason)) {
                        reason = detailReason;
                    }
                }
            }
        } catch (Exception ignored) {
            // Not JSON: fall through with what we have.
        }
        String all = (reason + " " + status + " " + message).toLowerCase();
        Kind kind = all.contains("accessnotconfigured") || all.contains("service_disabled")
                || all.contains("has not been used in project") || all.contains("is disabled")
                ? Kind.API_DISABLED
                : all.contains("insufficientpermissions") || all.contains("scope_insufficient")
                        || all.contains("insufficient authentication scopes")
                        ? Kind.SCOPE_MISSING
                        : Kind.OTHER;
        return new Details(kind, reason, message);
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }
}
