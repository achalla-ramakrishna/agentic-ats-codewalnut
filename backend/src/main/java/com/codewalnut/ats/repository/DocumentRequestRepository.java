package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.DocumentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, UUID> {

    List<DocumentRequest> findByCandidateIdOrderByRequestedAtDesc(UUID candidateId);

    List<DocumentRequest> findByCandidateIdAndFulfilledAtIsNull(UUID candidateId);

    List<DocumentRequest> findByCandidateIdIn(java.util.Collection<UUID> candidateIds);
}
