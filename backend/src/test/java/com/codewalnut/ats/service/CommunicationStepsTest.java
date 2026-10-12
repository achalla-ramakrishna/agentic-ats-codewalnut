package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.WorkflowDtos.Booking;
import com.codewalnut.ats.dto.WorkflowDtos.Communication;
import com.codewalnut.ats.dto.WorkflowDtos.InterviewStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PIPE-25: the outreach sequence and what "Today's emails" sends next. */
class CommunicationStepsTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private static Message sent(String template, int daysAgo) {
        return Message.builder().channel(MessageChannel.CANDIDATE).authorType(MessageAuthorType.STAFF).authorEmail("r@x")
                .body("b").emailed(true).templateKey(template).createdAt(NOW.minus(Duration.ofDays(daysAgo))).build();
    }

    private static Communication of(boolean hasResume, List<Message> messages) {
        return WorkflowService.communication(Stage.SOURCED, false, true, hasResume, messages, null, null, false, NOW);
    }

    @Test
    void walksThroughTheSequence() {
        assertThat(of(false, List.of())).isEqualTo(new Communication("NEW", "RESUME_REQUEST"));
        assertThat(of(false, List.of(sent("resume_request", 1)))).isEqualTo(new Communication("RESUME_ASKED", null));
        assertThat(of(false, List.of(sent("resume_request", 4)))).isEqualTo(new Communication("RESUME_ASKED", "RESUME_FOLLOW_UP"));
        assertThat(of(false, List.of(sent("resume_request", 6), sent("follow_up", 2)))).isEqualTo(new Communication("RESUME_ASKED", null));
        assertThat(of(true, List.of(sent("resume_request", 6)))).isEqualTo(new Communication("RESUME_RECEIVED", "BOOKING_LINK"));
        assertThat(of(true, List.of(sent("book_slot", 1)))).isEqualTo(new Communication("INVITED_TO_BOOK", null));
        assertThat(of(true, List.of(sent("book_slot", 4)))).isEqualTo(new Communication("INVITED_TO_BOOK", "BOOKING_REMINDER"));
        assertThat(of(true, List.of(sent("book_slot", 8), sent("booking_reminder", 4)))).isEqualTo(new Communication("INVITED_TO_BOOK", null));

        Booking pending = new Booking(NOW.plus(Duration.ofDays(1)), "Asia/Kolkata", false);
        assertThat(WorkflowService.communication(Stage.SOURCED, false, true, true, List.of(), pending, null, false, NOW))
                .isEqualTo(new Communication("BOOKED", "SEND_INVITE"));
        Booking invited = new Booking(NOW.plus(Duration.ofDays(1)), "Asia/Kolkata", true);
        assertThat(WorkflowService.communication(Stage.SOURCED, false, true, true, List.of(), invited, null, false, NOW))
                .isEqualTo(new Communication("SCHEDULED", null));
        InterviewStatus held = new InterviewStatus(null, "DONE", NOW.minus(Duration.ofDays(1)), 1, 1);
        assertThat(WorkflowService.communication(Stage.INTERVIEWED, false, true, true, List.of(), invited, held, false, NOW))
                .isEqualTo(new Communication("SCREENED", null));
    }

    @Test
    void anyEarlierEmailCountsAsAskedSoNobodyGetsTheFirstEmailTwice() {
        assertThat(of(false, List.of(sent(null, 1)))).isEqualTo(new Communication("RESUME_ASKED", null));
        assertThat(of(false, List.of(sent(null, 5)))).isEqualTo(new Communication("RESUME_ASKED", "RESUME_FOLLOW_UP"));
    }

    @Test
    void peopleWhoWroteOrLeftOrLaterStagesAreNotEmailedAutomatically() {
        assertThat(WorkflowService.communication(Stage.SOURCED, false, true, false, List.of(), null, null, true, NOW).due()).isNull();
        assertThat(WorkflowService.communication(Stage.REJECTED, true, true, false, List.of(), null, null, false, NOW).due()).isNull();
        assertThat(WorkflowService.communication(Stage.SOURCED, false, false, false, List.of(), null, null, false, NOW).due()).isNull();
        assertThat(WorkflowService.communication(Stage.SHORTLISTED, false, true, false, List.of(), null, null, false, NOW).due()).isNull();
    }
}
