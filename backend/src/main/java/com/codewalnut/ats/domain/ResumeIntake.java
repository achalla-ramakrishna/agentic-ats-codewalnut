package com.codewalnut.ats.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
 * A résumé uploaded in bulk to an opening, waiting to be read. Once read it becomes a candidate
 * in the opening with the résumé attached, and the file bytes here are cleared.
 */
@Entity
@Table(name = "resume_intake")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeIntake {

    public enum Status { PENDING, DONE, FAILED }

    /** What reading the résumé led to. */
    public enum Outcome { NEW_CANDIDATE, EXISTING_CANDIDATE, ALREADY_IN_OPENING }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "LONGBLOB")
    private byte[] data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Outcome outcome;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(length = 500)
    private String error;

    @Column(name = "uploaded_by", nullable = false, length = 254)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
