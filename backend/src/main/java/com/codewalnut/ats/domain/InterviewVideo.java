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

/** A recorded answer to a VIDEO question of an AI interview (ADR-0030). The file is on disk (VideoStorage). */
@Entity
@Table(name = "interview_video")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewVideo {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "invite_id", nullable = false)
    private UUID inviteId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    /** Path under the media folder, e.g. interviews/{invite}/{question}-{random}.webm. */
    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
