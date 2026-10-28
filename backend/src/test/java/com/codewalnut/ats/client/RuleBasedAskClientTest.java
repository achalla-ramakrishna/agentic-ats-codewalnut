package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RuleBasedAskClientTest {

    @Test
    void howToPrefersTheGuideLineAboutTheTopic() throws IOException {
        String guide = new ClassPathResource("assistant/ats-guide.md").getContentAsString(StandardCharsets.UTF_8);

        // The Naukri line also mentions "Schedule interview", but the Schedule line is about it.
        assertThat(RuleBasedAskClient.howTo("how do i schedule an interview?", guide))
                .startsWith("Schedule (Google Calendar + Meet invite)");
    }
}
