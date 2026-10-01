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

/** A person, reused across every opening they are considered for. */
@Entity
@Table(name = "candidate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    /** Lower-case; unique when present. */
    @Column(unique = true, length = 254)
    private String email;

    /** Digits only (plus an optional leading +). */
    @Column(length = 30)
    private String phone;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "current_address", length = 1000)
    private String currentAddress;

    @Column(name = "permanent_address", length = 1000)
    private String permanentAddress;

    @Column(length = 200)
    private String college;

    @Column(length = 200)
    private String degree;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    /** Name and phone of someone to contact in an emergency. */
    @Column(name = "emergency_contact", length = 300)
    private String emergencyContact;

    @Column(name = "profile_updated_at")
    private Instant profileUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
