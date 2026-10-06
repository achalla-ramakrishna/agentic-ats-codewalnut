package com.codewalnut.ats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ask ATS (ASK-01…). */
public final class AskDtos {

    private AskDtos() {}

    public record AskStatus(boolean available, List<String> suggestions) {}

    /** conversationId: null starts a new chat. */
    public record AskRequest(UUID conversationId, @NotBlank @Size(max = 2000) String question) {}

    /** role: "user" or "assistant". */
    public record ChatMessage(String role, String text, Instant at) {}

    public record ConversationSummary(UUID id, String title, Instant updatedAt) {}

    public record Conversation(UUID id, String title, List<ChatMessage> messages, Instant updatedAt) {}
}
