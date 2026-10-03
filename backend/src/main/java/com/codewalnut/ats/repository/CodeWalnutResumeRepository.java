package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.CodeWalnutResume;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeWalnutResumeRepository extends JpaRepository<CodeWalnutResume, UUID> {

    Optional<CodeWalnutResume> findByApplicationId(UUID applicationId);
}
