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

    /**
     * SINGLE_CHOICE: one right option. MULTI_CHOICE: pick all right options. SHORT_ANSWER: type it (e.g. the output).
     * CODING: write a program that reads stdin and prints the answer; it runs against hidden test cases (ADR-0016).
     */
    public enum Kind { SINGLE_CHOICE, MULTI_CHOICE, SHORT_ANSWER, CODING }

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

    /** Choice indexes, accepted answers, or (CODING) the hidden test cases. Never sent to candidates. */
    @Column(name = "answer_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String answerJson;

    /** CODING only: the public spec (languages, starter code, sample tests, limits) as JSON. */
    @Column(name = "coding_json", columnDefinition = "MEDIUMTEXT")
    private String codingJson;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** A picture shown with the question: an SVG the app drew, or an uploaded PNG/JPEG data URI. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String figure;

    /** Pictures for the options (same order as the options), when the options are figures. */
    @Column(name = "option_figures_json", columnDefinition = "MEDIUMTEXT")
    private String optionFiguresJson;

    /** e.g. QUANT, LOGICAL, VERBAL; from the bank, for section-wise scores. */
    @Column(length = 20)
    private String section;

    @Column(length = 60)
    private String topic;

    /** EASY, MEDIUM or HARD. */
    @Column(length = 10)
    private String difficulty;

    @Column(name = "bank_question_id")
    private java.util.UUID bankQuestionId;

    @Column(name = "ai_drafted", nullable = false)
    private boolean aiDrafted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
