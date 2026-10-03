package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.AssessmentInvite;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentInviteRepository extends JpaRepository<AssessmentInvite, UUID> {

    List<AssessmentInvite> findByApplicationIdOrderBySentAtDesc(UUID applicationId);

    List<AssessmentInvite> findByApplicationIdInOrderBySentAtDesc(Collection<UUID> applicationIds);

    List<AssessmentInvite> findByApplicationCandidateIdOrderBySentAtDesc(UUID candidateId);

    long countByAssessmentId(UUID assessmentId);
}
