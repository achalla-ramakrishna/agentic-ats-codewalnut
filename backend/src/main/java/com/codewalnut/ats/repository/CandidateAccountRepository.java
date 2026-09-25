package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.CandidateAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateAccountRepository extends JpaRepository<CandidateAccount, UUID> {

    Optional<CandidateAccount> findByEmail(String email);
}
