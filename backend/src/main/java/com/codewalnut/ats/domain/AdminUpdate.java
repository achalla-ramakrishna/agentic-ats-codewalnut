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

/** An FYI for admins (ADM-14…): a candidate summary sent when feedback comes in or a key stage is reached. */
@Entity
@Table(name = "admin_update")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUpdate {

    public enum Kind { FEEDBACK_SUBMITTED, STAGE_REACHED }

    /** SENT: emailed to every other admin. SKIPPED: the sender hadn't connected Gmail. NONE: nobody to email. */
    public enum EmailStatus { PENDING, SENT, SKIPPED, FAILED, NONE }

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "interview_id")
    private UUID interviewId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "actor_email", nullable = false, length = 254)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private EmailStatus emailStatus;

    @Column(name = "emailed_to", columnDefinition = "TEXT")
    private String emailedTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (emailStatus == null) {
            emailStatus = EmailStatus.PENDING;
        }
    }
}
