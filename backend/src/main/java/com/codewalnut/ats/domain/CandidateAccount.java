package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * A candidate's sign-in identity. Created on first sign-in with any verified Google account
 * (e.g. a personal Gmail). Candidates never get staff roles or staff APIs.
 */
@Entity
@Table(name = "candidate_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateAccount {

    @Id
    @UuidGenerator
    private UUID id;

    /** Always stored lower-case. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(length = 200)
    private String name;

    @Column(name = "google_subject", length = 255)
    private String googleSubject;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
