package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.InterviewKit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewKitRepository extends JpaRepository<InterviewKit, UUID> {

    Optional<InterviewKit> findByJobId(UUID jobId);
}
