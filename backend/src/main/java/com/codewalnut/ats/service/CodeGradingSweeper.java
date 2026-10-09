package com.codewalnut.ats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Retries code grading that's still pending, e.g. because the sandbox was down at submit time
 * (ADR-0016). Off when grading runs inline (ats.coding.async=false, as in tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ats.coding.async", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class CodeGradingSweeper {

    private final com.codewalnut.ats.config.OperationsMode operations;

    private final AssessmentInviteService invites;
    private final CodeRunService runs;

    @Scheduled(fixedDelayString = "${ats.coding.sweep-ms:60000}", initialDelayString = "${ats.coding.sweep-ms:60000}")
    public void sweep() {
        if (!operations.backgroundWorkEnabled()) {
            return;
        }
        invites.pendingGrading().forEach(runs::submit);
    }
}
