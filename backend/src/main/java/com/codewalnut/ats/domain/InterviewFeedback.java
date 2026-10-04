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

/** One panel member's feedback on one interview (INT-24…). Never shown to candidates or clients. */
@Entity
@Table(name = "interview_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewFeedback {

    /** HELD: the interview took place. The others record why there is no assessment. */
    public enum Attendance { HELD, CANDIDATE_NO_SHOW, INTERVIEWER_COULD_NOT_JOIN, ENDED_EARLY }

    public enum Recommendation { STRONG_NO, NO, YES, STRONG_YES }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "interview_id", nullable = false)
    private UUID interviewId;

    @Column(name = "author_email", nullable = false, length = 254)
    private String authorEmail;

    @Column(name = "author_name", length = 200)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Attendance attendance;

    /** [{competency, rating (1–4, or null = not assessed), note}] */
    @Column(name = "ratings_json", columnDefinition = "TEXT")
    private String ratingsJson;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Column(name = "questions_asked", columnDefinition = "TEXT")
    private String questionsAsked;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Recommendation recommendation;

    /** Anything else for the hiring team. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** True while the author is still filling it in (during the interview); private to them until submitted. */
    @Column(nullable = false)
    private boolean draft;

    /** When it was first saved; set again when a draft is submitted. */
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        submittedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
