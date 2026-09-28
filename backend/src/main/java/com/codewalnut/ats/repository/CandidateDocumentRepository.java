package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.DocumentKind;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, UUID> {

    /** File metadata without loading the file bytes. */
    interface Info {
        UUID getId();
        UUID getCandidateId();
        DocumentKind getKind();
        String getFileName();
        String getContentType();
        long getSizeBytes();
        String getUploadedBy();
        Instant getUploadedAt();
    }

    List<Info> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    @Query("select distinct d.candidateId, d.kind from CandidateDocument d where d.candidateId in :ids")
    List<Object[]> kindsFor(Collection<UUID> ids);
}
