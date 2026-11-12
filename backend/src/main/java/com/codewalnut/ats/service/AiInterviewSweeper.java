package com.codewalnut.ats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Retries AI interview reviews the AI couldn't do (busy or down) every few minutes (ADR-0030).
 * Off when reviews run inline (ats.interview.async=false, as in tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ats.interview.async", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AiInterviewSweeper {

    private final AiInterviewService interviews;
    private final AiInterviewReviewer reviewer;

    @Scheduled(fixedDelayString = "${ats.interview.sweep-ms:300000}", initialDelayString = "${ats.interview.sweep-ms:300000}")
    public void sweep() {
        interviews.pendingReviews().forEach(reviewer::submit);
    }
}
