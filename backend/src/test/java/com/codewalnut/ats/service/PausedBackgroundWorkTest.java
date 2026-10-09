package com.codewalnut.ats.service;

import static org.mockito.Mockito.*;

import com.codewalnut.ats.config.OperationsMode;
import com.codewalnut.ats.repository.BankQuestionRepository;
import com.codewalnut.ats.repository.ResumeIntakeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PausedBackgroundWorkTest {
    @Mock OperationsMode operations;
    @Mock BankQuestionRepository bankRepository;
    @Mock ResumeIntakeRepository intakeRepository;
    @Mock AssessmentInviteService invites;
    @Mock CodeRunService runs;
    @InjectMocks QuestionBankService questions;
    @InjectMocks ResumeIntelligenceService resumes;
    @InjectMocks CodeGradingSweeper sweeper;

    @Test
    void DEPLOY_03_pausedStartupAndSweepDoNotEvenQueryCopiedPendingWork() {
        when(operations.backgroundWorkEnabled()).thenReturn(false);
        questions.loadBuiltIn();
        resumes.resumeUnfinished();
        sweeper.sweep();
        verifyNoInteractions(bankRepository, intakeRepository, invites, runs);
    }

    @Test
    void DEPLOY_03_enabledSweepResumesPendingGrading() {
        when(operations.backgroundWorkEnabled()).thenReturn(true);
        java.util.UUID id = java.util.UUID.randomUUID();
        when(invites.pendingGrading()).thenReturn(java.util.List.of(id));
        sweeper.sweep();
        verify(runs).submit(id);
    }
}
