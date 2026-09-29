package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MimeMessagesTest {

    @Test
    void subjectCannotInjectHeaders() {
        String mime = MimeMessages.plainText(new MailClient.Email("a@example.com", "Hi\r\nBcc: evil@example.com", "body"));

        String headers = mime.substring(0, mime.indexOf("\r\n\r\n"));
        assertThat(headers).doesNotContain("Bcc:");
        assertThat(headers.lines()).hasSize(5);
        String encoded = headers.lines().filter(l -> l.startsWith("Subject:")).findFirst().orElseThrow();
        String subject = new String(Base64.getDecoder().decode(encoded.replace("Subject: =?UTF-8?B?", "").replace("?=", "")),
                StandardCharsets.UTF_8);
        assertThat(subject).isEqualTo("Hi Bcc: evil@example.com");
    }

    @Test
    void recipientMustBeOneSafeAddress() {
        for (String bad : new String[] {"a@example.com, b@example.com", "a@example.com\r\nBcc: x@y.z", "Name <a@example.com>", "nope"}) {
            assertThatThrownBy(() -> MimeMessages.plainText(new MailClient.Email(bad, "s", "b")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void bodyIsUtf8Base64() {
        String mime = MimeMessages.plainText(new MailClient.Email("a@example.com", "s", "Namaste 🙏\nLine two"));
        String body = mime.substring(mime.indexOf("\r\n\r\n") + 4).replace("\r\n", "");
        assertThat(new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8)).isEqualTo("Namaste 🙏\r\nLine two");
    }
}
