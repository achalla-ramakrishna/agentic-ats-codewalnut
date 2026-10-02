package com.codewalnut.ats.client;

/**
 * Sends WhatsApp messages through the WhatsApp Business API (ADR-0008). When the API isn't set
 * up, callers fall back to a click-to-chat link the recruiter sends from their own WhatsApp.
 */
public interface WhatsAppClient {

    /** to: digits only, with country code. Returns WhatsApp's message id. */
    record Outgoing(String to, String firstName, String jobTitle, String text, boolean freeForm) {}

    boolean apiEnabled();

    /**
     * freeForm: plain text, allowed only within 24 hours of the candidate's last WhatsApp message;
     * otherwise the approved template is used.
     */
    String send(Outgoing message);
}
