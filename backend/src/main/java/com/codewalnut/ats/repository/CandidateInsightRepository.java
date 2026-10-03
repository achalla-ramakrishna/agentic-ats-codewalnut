package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.CandidateInsight;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CandidateInsightRepository extends JpaRepository<CandidateInsight, UUID> {

    Optional<CandidateInsight> findByApplicationId(UUID applicationId);

    List<CandidateInsight> findByApplicationIdIn(Collection<UUID> applicationIds);

    @Query("select i.applicationId from CandidateInsight i where i.status = com.codewalnut.ats.domain.CandidateInsight.Status.PENDING")
    List<UUID> findPendingApplicationIds();
}
