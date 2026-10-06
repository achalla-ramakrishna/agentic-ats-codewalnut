package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** One person's chat with Ask ATS (ASK-01…). Private to its owner. */
@Entity
@Table(name = "ask_conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AskConversation {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_email", nullable = false, length = 254)
    private String ownerEmail;

    @Column(nullable = false, length = 120)
    private String title;

    /** The questions and answers, oldest first, as JSON. */
    @Column(name = "messages_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String messagesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
