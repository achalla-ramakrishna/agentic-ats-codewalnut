package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Client;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByNameIgnoreCase(String name);

    List<Client> findAllByOrderByNameAsc();
}
