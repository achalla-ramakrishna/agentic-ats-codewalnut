package com.codewalnut.ats.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * What CodeWalnut chose to share with a client about one application: the candidate's name,
 * opening and stage always; contact details, profile and specific document versions only when
 * ticked. The only way candidate data reaches a client (ADR-0007).
 */
@Entity
@Table(name = "client_share")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientShare {

    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "include_contact", nullable = false)
    private boolean includeContact;

    @Column(name = "include_profile", nullable = false)
    private boolean includeProfile;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "client_share_document", joinColumns = @JoinColumn(name = "share_id"))
    @Column(name = "document_id", nullable = false)
    private Set<UUID> documentIds = new LinkedHashSet<>();

    @Column(length = 1000)
    private String note;

    @Column(name = "shared_by", nullable = false, length = 254)
    private String sharedBy;

    @Column(name = "shared_at", nullable = false)
    private Instant sharedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
