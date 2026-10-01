package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.repository.ClientContactRepository;
import com.codewalnut.ats.security.SessionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in for people at a client whom staff added as contacts (ADR-0007). */
@Service
@RequiredArgsConstructor
public class ClientAuthService {

    private final ClientContactRepository contactRepository;
    private final AuditService auditService;

    /** @param email already normalised */
    @Transactional(readOnly = true)
    public Optional<ClientContact> activeContact(String email) {
        return contactRepository.findByEmail(email).filter(ClientContact::isActive);
    }

    @Transactional
    public ClientContact completeLogin(String email, String name) {
        ClientContact contact = activeContact(email).orElseThrow();
        if (contact.getName() == null && name != null) {
            contact.setName(name);
        }
        contact.setLastLoginAt(Instant.now());
        contactRepository.save(contact);
        auditService.recordAnonymous(email, AuditAction.CLIENT_LOGIN, Map.of("clientId", contact.getClient().getId()));
        return contact;
    }

    public static List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(SessionType.CLIENT.authority()));
    }
}
