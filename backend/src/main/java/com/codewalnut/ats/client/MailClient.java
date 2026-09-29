package com.codewalnut.ats.client;

/**
 * Sends an email from the signed-in staff member's own Gmail (ADR-0006). The only way the app
 * sends email. Replies go to their inbox.
 */
public interface MailClient {

    record Status(boolean available, boolean connected) {}

    /** Plain text only. {@code to} is always an address the server looked up, never user input. */
    record Email(String to, String subject, String body) {}

    Status status();

    /**
     * @return the provider's message id
     * @throws CalendarNotConnectedException when the user hasn't connected Google (or access expired)
     */
    String send(Email email);
}
