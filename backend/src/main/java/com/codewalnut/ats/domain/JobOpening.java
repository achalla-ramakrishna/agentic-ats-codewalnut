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

/** An opening candidates are hired into, e.g. "Blend – Interns". */
@Entity
@Table(name = "job_opening")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobOpening {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "hiring_type", nullable = false, length = 30)
    private HiringType hiringType;

    /** How many people are needed; null if open-ended. */
    private Integer openings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 20)
    private WorkMode workMode;

    /** Free text shown to candidates, e.g. "Internship · 6 months". */
    @Column(name = "employment_type", length = 100)
    private String employmentType;

    /** Random, unguessable id used in the shareable link /apply/{publicSlug}. */
    @Column(name = "public_slug", unique = true, length = 40)
    private String publicSlug;

    /** Whether the shareable link is live. */
    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_by", length = 254)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = JobStatus.OPEN;
        }
    }
}
