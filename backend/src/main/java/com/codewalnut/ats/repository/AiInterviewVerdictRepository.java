package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.AiInterviewVerdict;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInterviewVerdictRepository extends JpaRepository<AiInterviewVerdict, UUID> {

    List<AiInterviewVerdict> findByInviteIdOrderByCreatedAtAsc(UUID inviteId);

    List<AiInterviewVerdict> findByInviteIdInOrderByCreatedAtAsc(Collection<UUID> inviteIds);

    Optional<AiInterviewVerdict> findByInviteIdAndReviewerEmail(UUID inviteId, String reviewerEmail);
}
