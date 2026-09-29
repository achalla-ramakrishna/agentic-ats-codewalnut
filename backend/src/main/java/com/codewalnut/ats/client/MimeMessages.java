package com.codewalnut.ats.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/** Builds a minimal RFC 5322 plain-text message, safe against header injection. */
final class MimeMessages {

    private static final Pattern SAFE_ADDRESS = Pattern.compile("^[^@\\s,;<>\"()\\\\]+@[^@\\s,;<>\"()\\\\]+$");

    private MimeMessages() {}

    static String plainText(MailClient.Email email) {
        if (email.to() == null || !SAFE_ADDRESS.matcher(email.to()).matches()) {
            throw new IllegalArgumentException("email: the candidate's email address isn't valid");
        }
        String subject = email.subject() == null ? "" : email.subject().replaceAll("[\\r\\n\\t]+", " ").trim();
        String body = email.body() == null ? "" : email.body().replace("\r\n", "\n").replace("\n", "\r\n");
        return "To: " + email.to() + "\r\n"
                + "Subject: =?UTF-8?B?" + base64(subject) + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + Base64.getMimeEncoder(76, new byte[] {'\r', '\n'}).encodeToString(body.getBytes(StandardCharsets.UTF_8))
                + "\r\n";
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
