package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.AdminUpdate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUpdateRepository extends JpaRepository<AdminUpdate, UUID> {

    List<AdminUpdate> findTop100ByOrderByCreatedAtDesc();
}
