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

    /**
     * What a candidate or client contact sees: no staff email addresses, no ids of other records.
     */
    public record CandidateMessageResponse(
            boolean fromMe, String authorName, String subject, String body, Instant createdAt) {

        public static CandidateMessageResponse from(Message m) {
            return forViewer(m, MessageAuthorType.CANDIDATE, m.getAuthorEmail());
        }

        public static CandidateMessageResponse forViewer(Message m, MessageAuthorType viewer, String viewerEmail) {
            boolean mine = m.getAuthorType() == viewer && m.getAuthorEmail().equalsIgnoreCase(viewerEmail);
            String author = mine ? "You"
                    : m.getAuthorType() == MessageAuthorType.STAFF
                            ? (m.getAuthorName() != null ? m.getAuthorName() + ", CodeWalnut" : "CodeWalnut")
                            : (m.getAuthorName() != null ? m.getAuthorName() : m.getAuthorEmail());
            return new CandidateMessageResponse(mine, author, m.getSubject(), m.getBody(), m.getCreatedAt());
        }
    }

    /**
     * One conversation in the staff inbox: with a candidate (CANDIDATE) or with the client about a
     * candidate (CLIENT). lastFromExternal: the last message came from the candidate or client.
     */
    public record InboxItem(
            UUID applicationId, UUID jobId, String jobTitle, String candidateName, MessageChannel channel,
            String clientName, String lastAuthorName, boolean lastFromExternal, String preview, Instant lastAt,
            boolean awaitingReply) {}
}
