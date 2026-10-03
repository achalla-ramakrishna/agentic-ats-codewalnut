package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/** A test sent to one candidate for one opening, with their answers and score. */
@Entity
@Table(name = "assessment_invite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentInvite {

    /** EXPIRED: not started by the due date. CANCELLED: withdrawn by staff. */
    public enum Status { SENT, STARTED, SUBMITTED, EXPIRED, CANCELLED }

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "sent_by", nullable = false, length = 254)
    private String sentBy;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "started_at")
    private Instant startedAt;

    /** started_at + the test's duration. */
    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    private Integer percent;

    @Column(name = "answers_json", columnDefinition = "MEDIUMTEXT")
    private String answersJson;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    /** When someone who manages tests first looked at the result; null while it is a new result. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 254)
    private String reviewedBy;

    @PrePersist
    void onCreate() {
        sentAt = Instant.now();
    }
}
