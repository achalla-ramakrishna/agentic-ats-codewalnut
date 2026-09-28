package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Candidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmail(String email);

    List<Candidate> findByPhone(String phone);
}
