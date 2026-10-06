package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.ClientShare;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientShareRepository extends JpaRepository<ClientShare, UUID> {

    Optional<ClientShare> findByApplicationId(UUID applicationId);

    List<ClientShare> findByClientIdAndRevokedAtIsNullOrderBySharedAtDesc(UUID clientId);

    List<ClientShare> findByApplicationIdIn(java.util.Collection<UUID> applicationIds);
}
