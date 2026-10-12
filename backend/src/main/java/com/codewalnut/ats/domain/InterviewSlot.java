package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A time a recruiter offers for an interview in one opening (PIPE-24). applicationId is set when a
 * candidate books it; interviewId when the recruiter has sent the calendar invite.
 */
@Entity
@Table(name = "interview_slot")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InterviewSlot {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    /** Comma-separated, like Interview.interviewerEmails. */
    @Column(name = "interviewer_emails", length = 2000)
    private String interviewerEmails;

    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "booked_at")
    private Instant bookedAt;

    @Column(name = "interview_id")
    private UUID interviewId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
