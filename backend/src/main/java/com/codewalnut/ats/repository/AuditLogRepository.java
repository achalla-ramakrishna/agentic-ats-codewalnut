package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Deliberately not a CrudRepository: the audit log can be appended to and read, never changed. */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog entry);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByActionAndActorEmailOrderByCreatedAtDesc(AuditAction action, String actorEmail);
}
