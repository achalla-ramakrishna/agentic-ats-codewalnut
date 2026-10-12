package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.InterviewSlot;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewSlotRepository extends JpaRepository<InterviewSlot, UUID> {

    List<InterviewSlot> findByJobIdOrderByStartAtAsc(UUID jobId);

    Optional<InterviewSlot> findByApplicationId(UUID applicationId);

    List<InterviewSlot> findByApplicationIdIn(Collection<UUID> applicationIds);

    boolean existsByJobIdAndStartAt(UUID jobId, Instant startAt);

    /** Books a free slot; 0 when someone else got it first. */
    @Modifying(clearAutomatically = true)
    @Query("update InterviewSlot s set s.applicationId = :applicationId, s.bookedAt = :now"
            + " where s.id = :slotId and s.applicationId is null")
    int book(@Param("slotId") UUID slotId, @Param("applicationId") UUID applicationId, @Param("now") Instant now);
}
