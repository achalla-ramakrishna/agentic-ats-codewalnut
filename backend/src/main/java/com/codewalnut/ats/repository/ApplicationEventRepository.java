package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.ApplicationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Append-only: save and read, no update or delete. */
public interface ApplicationEventRepository extends Repository<ApplicationEvent, UUID> {

    ApplicationEvent save(ApplicationEvent event);

    List<ApplicationEvent> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    List<ApplicationEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ApplicationEvent> findByApplicationIdIn(java.util.Collection<UUID> applicationIds);
}
