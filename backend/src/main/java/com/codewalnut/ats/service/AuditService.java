package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.AuditLog;
import com.codewalnut.ats.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records an entry in its own transaction, so denials and rejected sign-ins are kept even
     * when the surrounding request fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AppUser actor, AuditAction action, String entityType, Object entityId,
            Map<String, ?> details) {
        write(actor, actor == null ? null : actor.getEmail(), action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAnonymous(String actorEmail, AuditAction action, Map<String, ?> details) {
        write(null, actorEmail, action, null, null, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private void write(AppUser actor, String actorEmail, AuditAction action, String entityType,
            Object entityId, Map<String, ?> details) {
        auditLogRepository.save(AuditLog.builder()
                .actorId(actor == null ? null : actor.getId())
                .actorEmail(actorEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId == null ? null : entityId.toString())
                .details(toJson(details))
                .build());
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Audit details are not serialisable", ex);
        }
    }
}
