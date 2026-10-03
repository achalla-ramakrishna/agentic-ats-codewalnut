package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.ClientContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientContactRepository extends JpaRepository<ClientContact, UUID> {

    Optional<ClientContact> findByEmail(String email);

    List<ClientContact> findByActiveTrueOrderByEmailAsc();

    List<ClientContact> findByClientIdOrderByEmailAsc(UUID clientId);
}
