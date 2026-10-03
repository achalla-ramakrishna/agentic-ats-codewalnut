package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Assessment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findAllByOrderByUpdatedAtDesc();
}
