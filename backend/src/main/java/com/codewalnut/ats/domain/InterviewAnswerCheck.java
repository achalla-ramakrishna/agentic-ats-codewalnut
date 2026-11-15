package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One spoken question of an AI interview as the candidate met it (AINT-24, ADR-0031): when they saw it,
 * how many takes, how long they thought, whether they left the window while recording, and the AI's
 * advisory check of whether the answer sounds read out. Signals for people, never a decision.
 */
@Entity
@Table(name = "interview_answer_check")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewAnswerCheck {

    /** The AI's reading check of an answer. */
    public enum Reading {
        PENDING,
        /** Several signs that it was read from prepared or generated text. */
        LIKELY,
        /** Some signs; watch the video. */
        POSSIBLE,
        UNLIKELY,
        /** Too short to judge, or no AI. */
        SKIPPED,
        FAILED
    }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "invite_id", nullable = false)
    private UUID inviteId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "revealed_at", nullable = false)
    private Instant revealedAt;

    @Column(nullable = false)
    private int takes;

    @Column(name = "think_seconds")
    private Integer thinkSeconds;

    @Column(name = "focus_losses", nullable = false)
    private int focusLosses;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Reading reading;

    @Column(name = "reading_reason", length = 600)
    private String readingReason;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
