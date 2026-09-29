package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class MessageDtos {

    private MessageDtos() {}

    /** sendEmail: also email it to the candidate from the sender's Gmail (CANDIDATE channel only). */
    public record PostMessageRequest(
            @NotNull MessageChannel channel,
            @NotBlank @Size(max = 10_000) String body,
            @Size(max = 300) String subject,
            boolean sendEmail) {}

    public record CandidatePostRequest(@NotBlank @Size(max = 5_000) String body) {}

    public record MessageResponse(
            UUID id, MessageChannel channel, MessageAuthorType authorType, String authorEmail, String authorName,
            String subject, String body, boolean emailed, Instant createdAt) {

        public static MessageResponse from(Message m) {
            return new MessageResponse(m.getId(), m.getChannel(), m.getAuthorType(), m.getAuthorEmail(),
                    m.getAuthorName(), m.getSubject(), m.getBody(), m.isEmailed(), m.getCreatedAt());
        }
    }

    /** What a candidate sees: no staff email addresses, no ids of other records. */
    public record CandidateMessageResponse(
            boolean fromMe, String authorName, String subject, String body, Instant createdAt) {

        public static CandidateMessageResponse from(Message m) {
            boolean mine = m.getAuthorType() == MessageAuthorType.CANDIDATE;
            String author = mine ? "You"
                    : (m.getAuthorName() != null ? m.getAuthorName() + ", CodeWalnut" : "CodeWalnut");
            return new CandidateMessageResponse(mine, author, m.getSubject(), m.getBody(), m.getCreatedAt());
        }
    }

    /** One candidate conversation in the staff inbox. */
    public record InboxItem(
            UUID applicationId, UUID jobId, String jobTitle, String candidateName, String lastAuthorName,
            boolean lastFromCandidate, String preview, Instant lastAt, boolean awaitingReply) {}
}
