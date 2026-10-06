package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.DocumentRequest;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.WorkflowDtos.ClientStatus;
import com.codewalnut.ats.dto.WorkflowDtos.Contact;
import com.codewalnut.ats.dto.WorkflowDtos.InterviewStatus;
import com.codewalnut.ats.dto.WorkflowDtos.NextStep;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The suggested next steps in the workflow view (WF-04). */
class WorkflowRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-06T06:00:00Z");
    private static final Contact CONTACTED = new Contact(NOW.minus(Duration.ofDays(1)), "Email", "recruiter@codewalnut.test");

    private static NextStep step(Stage stage, boolean clientOpening, AssessmentInvite invite, InterviewStatus interview,
            Interview interviewRow, ClientStatus client, List<DocumentRequest> docs, Duration inStage) {
        return WorkflowService.nextStep(stage, clientOpening, CONTACTED, false, invite, interview, interviewRow, client, docs,
                NOW.minus(inStage), NOW.minus(Duration.ofDays(10)), NOW);
    }

    private static AssessmentInvite invite(AssessmentInvite.Status status, Duration sentAgo, Integer percent) {
        return AssessmentInvite.builder().status(status).sentAt(NOW.minus(sentAgo)).percent(percent)
                .assessment(Assessment.builder().title("Java basics").passPercent(60).build()).build();
    }

    @Test
    void testsAreChasedThenReviewed() {
        assertThat(step(Stage.SCREENING, false, invite(AssessmentInvite.Status.SENT, Duration.ofHours(5), null), null, null, null,
                List.of(), Duration.ofDays(1)).code()).isEqualTo("WAIT_TEST");
        NextStep remind = step(Stage.SCREENING, false, invite(AssessmentInvite.Status.SENT, Duration.ofDays(3), null), null, null,
                null, List.of(), Duration.ofDays(3));
        assertThat(remind.code()).isEqualTo("REMIND_TEST");
        assertThat(remind.urgent()).isTrue();
        assertThat(step(Stage.SCREENING, false, invite(AssessmentInvite.Status.SUBMITTED, Duration.ofDays(1), 80), null, null, null,
                List.of(), Duration.ofDays(1)).code()).isEqualTo("REVIEW_TEST");
    }

    @Test
    void interviewsNeedFeedbackThenADecision() {
        Interview held = Interview.builder().startAt(NOW.minus(Duration.ofDays(2))).endAt(NOW.minus(Duration.ofDays(2)).plusSeconds(2700)).build();
        NextStep feedback = step(Stage.SCREENING, false, null, new InterviewStatus(null, "DONE", held.getStartAt(), 0, 2), held, null,
                List.of(), Duration.ofDays(4));
        assertThat(feedback.code()).isEqualTo("FEEDBACK");
        assertThat(feedback.urgent()).isTrue();
        assertThat(step(Stage.INTERVIEWED, false, null, new InterviewStatus(null, "DONE", held.getStartAt(), 2, 2), held, null,
                List.of(), Duration.ofDays(1)).code()).isEqualTo("DECIDE");
    }

    @Test
    void clientOpeningsShareThenFollowUp() {
        assertThat(step(Stage.SHORTLISTED, true, null, null, null, null, List.of(), Duration.ofDays(1)).code()).isEqualTo("SHARE");
        NextStep followUp = step(Stage.SUBMITTED_TO_CLIENT, true, null, null, null,
                new ClientStatus("Blend", NOW.minus(Duration.ofDays(5)), null), List.of(), Duration.ofDays(5));
        assertThat(followUp.code()).isEqualTo("CLIENT_FOLLOW_UP");
        assertThat(followUp.urgent()).isTrue();
        assertThat(step(Stage.SHORTLISTED, false, null, null, null, null, List.of(), Duration.ofDays(1)).code()).isEqualTo("DECIDE");
    }

    @Test
    void documentsAndOffers() {
        DocumentRequest old = DocumentRequest.builder().requestedAt(NOW.minus(Duration.ofDays(5))).build();
        assertThat(step(Stage.OFFER_ACCEPTED, false, null, null, null, null, List.of(old), Duration.ofDays(5)).code()).isEqualTo("CHASE_DOCS");
        assertThat(step(Stage.SELECTED, false, null, null, null, null, List.of(), Duration.ofDays(3)).urgent()).isTrue();
        assertThat(step(Stage.JOINED, false, null, null, null, null, List.of(), Duration.ofDays(3))).isNull();
    }
}
