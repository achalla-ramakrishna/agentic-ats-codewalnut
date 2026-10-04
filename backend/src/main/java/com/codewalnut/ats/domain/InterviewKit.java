package com.codewalnut.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** An opening's interview kit (INT-28…), generated from its job description. Staff only. */
@Entity
@Table(name = "interview_kit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewKit {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    /** The generated kit (InterviewKitDtos.KitContent). */
    @Column(name = "kit_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String kitJson;

    /** Hash of the title and description it was made from, to spot a changed job description. */
    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "generated_by", nullable = false, length = 254)
    private String generatedBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
