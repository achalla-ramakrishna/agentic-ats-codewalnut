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

    /**
     * sendEmail: also email it from the sender's Gmail; sendWhatsApp: also WhatsApp it to the
     * candidate's mobile (both CANDIDATE channel only).
     */
    public record PostMessageRequest(
            @NotNull MessageChannel channel,
            @NotBlank @Size(max = 10_000) String body,
            @Size(max = 300) String subject,
            boolean sendEmail,
            boolean sendWhatsApp) {}

    public record CandidatePostRequest(@NotBlank @Size(max = 5_000) String body) {}

    /**
     * whatsapp: delivery status (see Message). whatsappLink and warnings are only set when sending:
     * the link opens WhatsApp with the text ready (when the Business API isn't set up), and
     * warnings say which channel failed when another one worked.
     */
    public record MessageResponse(
            UUID id, MessageChannel channel, MessageAuthorType authorType, String authorEmail, String authorName,
            String subject, String body, boolean emailed, String whatsapp, Instant createdAt, String whatsappLink,
            java.util.List<String> warnings) {

        public static MessageResponse from(Message m) {
            return sent(m, null, java.util.List.of());
        }

        public static MessageResponse sent(Message m, String whatsappLink, java.util.List<String> warnings) {
            return new MessageResponse(m.getId(), m.getChannel(), m.getAuthorType(), m.getAuthorEmail(),
                    m.getAuthorName(), m.getSubject(), m.getBody(), m.isEmailed(), m.getWhatsappStatus(),
                    m.getCreatedAt(), whatsappLink, warnings);
        }
    }

    public record WhatsAppStatusResponse(boolean apiEnabled, boolean repliesEnabled) {}

    /**
     * What a candidate or client contact sees: no staff email addresses, no ids of other records.
     */
    public record CandidateMessageResponse(
            boolean fromMe, String authorName, String subject, String body, Instant createdAt, boolean viaWhatsApp) {

        public static CandidateMessageResponse from(Message m) {
            return forViewer(m, MessageAuthorType.CANDIDATE, m.getAuthorEmail());
        }

        public static CandidateMessageResponse forViewer(Message m, MessageAuthorType viewer, String viewerEmail) {
            boolean mine = m.getAuthorType() == viewer && m.getAuthorEmail().equalsIgnoreCase(viewerEmail);
            String author = mine ? "You"
                    : m.getAuthorType() == MessageAuthorType.STAFF
                            ? (m.getAuthorName() != null ? m.getAuthorName() + ", CodeWalnut" : "CodeWalnut")
                            : (m.getAuthorName() != null ? m.getAuthorName() : m.getAuthorEmail());
            return new CandidateMessageResponse(mine, author, m.getSubject(), m.getBody(), m.getCreatedAt(),
                    m.getWhatsappStatus() != null);
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
