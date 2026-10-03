package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** The editable CodeWalnut résumé for one candidate in one opening (ADR-0012). */
@Entity
@Table(name = "codewalnut_resume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeWalnutResume {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    /** The {@code BrandedResume} as JSON. */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String data;

    @Column(name = "show_email", nullable = false)
    private boolean showEmail;

    @Column(name = "include_screening", nullable = false)
    private boolean includeScreening;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "saved_document_id")
    private UUID savedDocumentId;

    @Column(length = 100)
    private String model;

    @Column(name = "updated_by", nullable = false, length = 254)
    private String updatedBy;

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
