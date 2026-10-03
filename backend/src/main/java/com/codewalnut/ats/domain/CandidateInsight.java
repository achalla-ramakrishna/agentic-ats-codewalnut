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
 * The AI's reading of one candidate's résumé for one opening (ADR-0010). Advisory only: it
 * helps people decide whom to contact first; it never moves or rejects anyone.
 */
@Entity
@Table(name = "candidate_insight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateInsight {

    public enum Status { PENDING, DONE, FAILED, NO_RESUME }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "fit_percent")
    private Integer fitPercent;

    @Column(length = 300)
    private String headline;

    /** The structured profile ({@code ResumeInsight}) as JSON. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String data;

    @Column(length = 500)
    private String error;

    @Column(length = 100)
    private String model;

    /** Hash of the job title and description the résumé was read against, to spot stale readings. */
    @Column(name = "job_hash", length = 64)
    private String jobHash;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

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
