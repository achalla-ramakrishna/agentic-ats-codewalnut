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

/** A reviewer's verdict on a completed AI interview (AINT-21). Never shown to candidates or clients. */
@Entity
@Table(name = "ai_interview_verdict")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInterviewVerdict {

    public enum Decision {
        SELECT("Select for next round"),
        HOLD("Maybe — discuss"),
        REJECT("Don't progress");

        private final String label;

        Decision(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "invite_id", nullable = false)
    private UUID inviteId;

    @Column(name = "reviewer_email", nullable = false, length = 254)
    private String reviewerEmail;

    @Column(name = "reviewer_name", length = 200)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    /** [{position, mark (GOOD|OK|WEAK), note}] */
    @Column(name = "marks_json", columnDefinition = "TEXT")
    private String marksJson;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "created_at", nullable = false)
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
