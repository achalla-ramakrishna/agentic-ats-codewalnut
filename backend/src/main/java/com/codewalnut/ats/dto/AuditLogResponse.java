package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id, Instant createdAt, String actorEmail, AuditAction action, String entityType,
        String entityId, String details) {

    public static AuditLogResponse from(AuditLog entry) {
        return new AuditLogResponse(entry.getId(), entry.getCreatedAt(), entry.getActorEmail(),
                entry.getAction(), entry.getEntityType(), entry.getEntityId(), entry.getDetails());
    }
}
