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

/** One question of a test. The answer is never sent to candidates. */
@Entity
@Table(name = "assessment_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentQuestion {

    /** SINGLE_CHOICE: one right option. MULTI_CHOICE: pick all right options. SHORT_ANSWER: type it (e.g. the output). */
    public enum Kind { SINGLE_CHOICE, MULTI_CHOICE, SHORT_ANSWER }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    /** Optional code to read, shown in a monospace block. */
    @Column(columnDefinition = "TEXT")
    private String code;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "answer_json", nullable = false, columnDefinition = "TEXT")
    private String answerJson;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "ai_drafted", nullable = false)
    private boolean aiDrafted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
