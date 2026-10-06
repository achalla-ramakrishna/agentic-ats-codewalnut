package com.codewalnut.ats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ask ATS (ASK-01…). */
public final class AskDtos {

    private AskDtos() {}

    public record AskStatus(boolean available, List<String> suggestions) {}

    /** conversationId: null starts a new chat. */
    public record AskRequest(UUID conversationId, @NotBlank @Size(max = 2000) String question) {}

    /** role: "user" or "assistant". actions: what the assistant proposed in this answer (ASK-06), or null. */
    public record ChatMessage(String role, String text, Instant at, List<ProposedAction> actions) {

        public ChatMessage(String role, String text, Instant at) {
            this(role, text, at, null);
        }

        public ChatMessage withActions(List<ProposedAction> next) {
            return new ChatMessage(role, text, at, next);
        }
    }

    /**
     * An action Ask ATS proposed (ASK-06…ASK-09). Nothing happens until the person clicks Do it.
     * type: MOVE_STAGE, ADD_NOTE, LOG_CONTACT, REMIND_TEST, MESSAGE, SHARE_WITH_CLIENT.
     * status: PENDING, DONE, SKIPPED or FAILED; result says what happened.
     */
    public record ProposedAction(String id, String type, UUID applicationId, UUID jobId, String candidateName, String jobTitle,
            String summary, Map<String, String> params, String status, String result, String doneBy, Instant doneAt) {

        public ProposedAction finish(String nextStatus, String nextResult, String by) {
            return new ProposedAction(id, type, applicationId, jobId, candidateName, jobTitle, summary, params, nextStatus,
                    nextResult, by, Instant.now());
        }
    }

    /** decision: "do" or "skip". subject and body: the person's edits to a message before it goes. */
    public record ActionRequest(@NotBlank String decision, @Size(max = 300) String subject, @Size(max = 10_000) String body) {}

    /** whatsappLink: open it to send (click-to-chat, when the WhatsApp Business API is off). */
    public record ActionOutcome(Conversation conversation, String whatsappLink) {}

    public record ConversationSummary(UUID id, String title, Instant updatedAt) {}

    public record Conversation(UUID id, String title, List<ChatMessage> messages, Instant updatedAt) {}
}
