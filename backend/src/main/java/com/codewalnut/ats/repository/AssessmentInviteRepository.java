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

    List<AssessmentInvite> findByAssessmentId(UUID assessmentId);

    List<AssessmentInvite> findByStatus(AssessmentInvite.Status status);

    /** Submitted results nobody has looked at yet, newest first. */
    List<AssessmentInvite> findByStatusAndReviewedAtIsNullOrderBySubmittedAtDesc(AssessmentInvite.Status status);

    /** Invites someone has started or submitted: the test has been taken. */
    long countByAssessmentIdAndStatusIn(UUID assessmentId, Collection<AssessmentInvite.Status> statuses);
}
