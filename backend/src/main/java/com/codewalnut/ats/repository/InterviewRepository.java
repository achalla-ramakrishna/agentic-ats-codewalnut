package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationIdOrderByStartAtDesc(UUID applicationId);

    List<Interview> findByApplicationJobId(UUID jobId);

    List<Interview> findByStatusAndEndAtAfterOrderByStartAtAsc(InterviewStatus status, Instant after);

    List<Interview> findByApplicationCandidateIdAndStatusAndEndAtAfterOrderByStartAtAsc(
            UUID candidateId, InterviewStatus status, Instant after);

    List<Interview> findByApplicationIdIn(java.util.Collection<UUID> applicationIds);
}
