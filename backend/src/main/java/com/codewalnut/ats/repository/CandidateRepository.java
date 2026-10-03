package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Candidate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmail(String email);

    List<Candidate> findByPhone(String phone);

    /** Candidates with an email (they can sign in), matching name or email, for "view as". */
    @org.springframework.data.jpa.repository.Query("""
            select c from Candidate c
            where c.email is not null
              and (:q is null or lower(c.name) like lower(concat('%', :q, '%')) or c.email like lower(concat('%', :q, '%')))
            order by c.createdAt desc
            """)
    List<Candidate> searchWithEmail(String q, org.springframework.data.domain.Pageable page);
}
