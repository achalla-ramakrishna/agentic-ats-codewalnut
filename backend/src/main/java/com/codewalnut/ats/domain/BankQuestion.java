package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * A reusable question in the bank (ADR-0014). Test papers copy questions from here, so editing
 * or archiving a bank question never changes a test that was already sent.
 */
@Entity
@Table(name = "bank_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankQuestion {

    /**
     * Aptitude uses the campus-test sections (numerical, logical, verbal). Technical areas (Java,
     * Python, SQL …) use experience bands: fundamentals for freshers, applied for 1–3 years,
     * advanced for 3+ years.
     */
    public enum Section {
        QUANT("Numerical ability", null),
        LOGICAL("Logical reasoning", null),
        VERBAL("Verbal ability", null),
        FUNDAMENTALS("Fundamentals", "Freshers"),
        PRACTICAL("Applied", "1–3 years"),
        ADVANCED("Advanced", "3+ years");

        private final String label;
        private final String level;

        Section(String label, String level) {
            this.label = label;
            this.level = level;
        }

        public String getLabel() {
            return label;
        }

        /** Who the band is for (technical areas only), e.g. "Freshers". */
        public String getLevel() {
            return level;
        }

        public boolean isAptitude() {
            return level == null;
        }

        /** The sections a question in this area may use. */
        public static java.util.List<Section> forArea(Assessment.Category area) {
            return java.util.Arrays.stream(values()).filter(s -> s.isAptitude() == (area == Assessment.Category.APTITUDE)).toList();
        }
    }

    public enum Difficulty { EASY, MEDIUM, HARD }

    /** BUILT_IN: CodeWalnut's starter bank. AI: drafted by the AI and reviewed. MANUAL: written by staff. */
    public enum Source { BUILT_IN, AI, MANUAL }

    /** REVIEW: drafted by the AI and not yet checked by a person; never used in papers until approved. */
    public enum Status { ACTIVE, REVIEW, ARCHIVED }

    @Id
    @UuidGenerator
    private UUID id;

    /** The test area, e.g. APTITUDE (same values as Assessment.Category). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Assessment.Category area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Section section;

    @Column(nullable = false, length = 60)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssessmentQuestion.Kind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String code;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String figure;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "option_figures_json", columnDefinition = "MEDIUMTEXT")
    private String optionFiguresJson;

    @Column(name = "answer_json", nullable = false, columnDefinition = "TEXT")
    private String answerJson;

    @Column(nullable = false)
    private int points;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    /** Stable id of a built-in question, so the starter bank is added once and can be updated. */
    @Column(name = "builtin_key", length = 80, unique = true)
    private String builtinKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "times_used", nullable = false)
    private int timesUsed;

    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
