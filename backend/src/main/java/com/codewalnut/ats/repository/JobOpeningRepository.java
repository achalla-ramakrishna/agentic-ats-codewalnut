package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.JobOpening;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOpeningRepository extends JpaRepository<JobOpening, UUID> {

    List<JobOpening> findAllByOrderByCreatedAtDesc();

    Optional<JobOpening> findByPublicSlug(String publicSlug);
}
