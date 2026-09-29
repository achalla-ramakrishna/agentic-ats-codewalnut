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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** An interview for one application, held as an event on the organiser's Google Calendar. */
@Entity
@Table(name = "interview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    /** Comma-separated, lower-case. */
    @Column(name = "interviewer_emails", length = 2600)
    private String interviewerEmails;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status;

    @Column(name = "meet_link", length = 500)
    private String meetLink;

    @Column(name = "calendar_event_id", length = 255)
    private String calendarEventId;

    @Column(name = "calendar_link", length = 1000)
    private String calendarLink;

    @Column(name = "organizer_email", nullable = false, length = 254)
    private String organizerEmail;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public List<String> interviewers() {
        if (interviewerEmails == null || interviewerEmails.isBlank()) {
            return List.of();
        }
        return Arrays.stream(interviewerEmails.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
