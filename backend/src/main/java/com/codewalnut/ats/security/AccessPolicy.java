package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.service.AuditService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The single place access decisions are made. Services call this; controllers and the UI
 * never re-implement checks. Every denial is audited.
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    private final AuditService auditService;

    public boolean has(AppUser user, Capability capability) {
        return user.isActive() && RolePermissions.capabilitiesFor(user.getRoles()).contains(capability);
    }

    public void require(AppUser user, Capability capability) {
        if (!has(user, capability)) {
            auditService.record(user, AuditAction.ACCESS_DENIED, null, null,
                    Map.of("capability", capability.name()));
            throw new AccessDeniedException("Missing permission: " + capability.name());
        }
    }
}
