package com.codewalnut.ats.client;

import com.anthropic.errors.AnthropicServiceException;
import java.util.Locale;

/**
 * Turns an error from the AI service into a reason a recruiter (or whoever runs the app) can act
 * on, e.g. a rejected key or an empty credit balance, instead of a bare "unavailable".
 */
public final class AiErrors {

    private AiErrors() {}

    public static String reason(AnthropicServiceException e) {
        return reason(e.statusCode(), e.getMessage());
    }

    /** Null for a 400 that isn't about the account, so callers can keep their own "bad input" message. */
    static String reason(int status, String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("credit balance") || m.contains("billing")) {
            return "the Anthropic account has run out of credit — add credit at console.anthropic.com → Billing";
        }
        return switch (status) {
            case 401 -> "the AI key was rejected — check ANTHROPIC_API_KEY on Railway";
            case 403 -> "the AI key isn't allowed to do this — check the key's workspace and limits at console.anthropic.com";
            case 404 -> "the AI model isn't available to this key — check ATS_ASSISTANT_MODEL, or remove it to use the default";
            case 413 -> "the file is too large for the AI";
            case 429 -> "the AI is busy (rate limit) — try again in a minute";
            case 400 -> null;
            default -> status >= 500 ? "the AI service is overloaded or down (HTTP " + status + ") — try again in a few minutes"
                    : "the AI service returned HTTP " + status;
        };
    }
}
