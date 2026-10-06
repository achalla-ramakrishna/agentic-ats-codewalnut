package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What must never reach a client résumé. */
class CodeWalnutResumeServiceTest {

    @Test
    void phoneNumbersAndLinksAreRemovedButDatesAndScoresStay() {
        assertThat(CodeWalnutResumeService.strip("Bengaluru | +91 98765 43210 | asha@x.test")).isEqualTo("Bengaluru | asha@x.test");
        assertThat(CodeWalnutResumeService.strip("Mobile: 9876543210")).isEmpty();
        assertThat(CodeWalnutResumeService.strip("Call (080) 4123-4567 now")).isEqualTo("Call now");
        assertThat(CodeWalnutResumeService.strip("See linkedin.com/in/asha and https://github.com/asha")).isEqualTo("See and");
        assertThat(CodeWalnutResumeService.strip("2022 - 2026 | CGPA: 7.8/10")).isEqualTo("2022 - 2026 | CGPA: 7.8/10");
        assertThat(CodeWalnutResumeService.strip("Served 200+ peers in 2023")).isEqualTo("Served 200+ peers in 2023");
    }

    @Test
    void keepsOnlyAGithubOrPortfolioLink() {
        assertThat(CodeWalnutResumeService.link("https://www.github.com/asha/")).isEqualTo("github.com/asha");
        assertThat(CodeWalnutResumeService.link("asha.dev")).isEqualTo("asha.dev");
        assertThat(CodeWalnutResumeService.link("https://linkedin.com/in/asha")).isEmpty();
        assertThat(CodeWalnutResumeService.link("in.linkedin.com/in/asha")).isEmpty();
        assertThat(CodeWalnutResumeService.link("call me maybe")).isEmpty();
        assertThat(CodeWalnutResumeService.link(null)).isEmpty();
    }
}
