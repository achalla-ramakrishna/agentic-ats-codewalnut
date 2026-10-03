package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.ResumeIntake;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ResumeIntakeRepository extends JpaRepository<ResumeIntake, UUID> {

    /** Intake rows without the file bytes. */
    interface Info {
        UUID getId();
        String getFileName();
        ResumeIntake.Status getStatus();
        ResumeIntake.Outcome getOutcome();
        UUID getApplicationId();
        String getError();
        Instant getCreatedAt();
    }

    List<Info> findByJobIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID jobId, Instant after);

    @Query("select i.id from ResumeIntake i where i.status = com.codewalnut.ats.domain.ResumeIntake.Status.PENDING")
    List<UUID> findPendingIds();
}
