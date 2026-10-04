package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** A live coding room for one interview (INT-36…). Notes are staff-only. */
@Entity
@Table(name = "coding_room")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingRoom {

    public enum Status { OPEN, ENDED }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "interview_id", nullable = false)
    private UUID interviewId;

    /** The unguessable part of the candidate's link. */
    @Column(nullable = false, length = 40)
    private String token;

    /** A built-in coding problem's id, or null for a problem typed by the interviewer. */
    @Column(name = "problem_id", length = 80)
    private String problemId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    /** The public coding spec: languages, starter code, sample tests, limits. Never hidden tests. */
    @Column(name = "spec_json", nullable = false, columnDefinition = "TEXT")
    private String specJson;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "run_count", nullable = false)
    private int runCount;

    @Column(name = "last_run_json", columnDefinition = "MEDIUMTEXT")
    private String lastRunJson;

    /** Earlier problems in this room: [{title, language, code, passed, total}]. */
    @Column(name = "history_json", columnDefinition = "MEDIUMTEXT")
    private String historyJson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "code_updated_at")
    private Instant codeUpdatedAt;

    @Column(name = "candidate_seen_at")
    private Instant candidateSeenAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
