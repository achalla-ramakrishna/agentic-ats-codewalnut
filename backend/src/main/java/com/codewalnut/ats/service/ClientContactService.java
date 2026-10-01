package com.codewalnut.ats.service;

import com.codewalnut.ats.config.AuthProperties;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Client;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.dto.ClientDtos.ClientContactResponse;
import com.codewalnut.ats.repository.CandidateAccountRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.ClientContactRepository;
import com.codewalnut.ats.repository.ClientRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** People at a client who may sign in to see what CodeWalnut shared with them (ADR-0007). */
@Service
@RequiredArgsConstructor
public class ClientContactService {

    private final ClientRepository clientRepository;
    private final ClientContactRepository contactRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateAccountRepository candidateAccountRepository;
    private final AuthProperties authProperties;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ClientContactResponse> list(AppUser actor, UUID clientId) {
        accessPolicy.require(actor, Capability.VIEW_CLIENTS);
        client(clientId);
        return contactRepository.findByClientIdOrderByEmailAsc(clientId).stream().map(ClientContactResponse::from).toList();
    }

    @Transactional
    public ClientContactResponse add(AppUser actor, UUID clientId, String rawEmail, String name) {
        accessPolicy.require(actor, Capability.MANAGE_CLIENTS);
        Client client = client(clientId);
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        if (authProperties.isAllowedEmail(email)) {
            throw new IllegalArgumentException("email: that's a CodeWalnut address; add staff under Users instead");
        }
        if (candidateRepository.findByEmail(email).isPresent() || candidateAccountRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("This email belongs to a candidate, so it can't be a client contact");
        }
        ClientContact contact = contactRepository.findByEmail(email).orElse(null);
        if (contact != null && (contact.isActive() || !contact.getClient().getId().equals(clientId))) {
            throw new ConflictException(email + " is already a contact for " + contact.getClient().getName());
        }
        if (contact == null) {
            contact = ClientContact.builder().client(client).email(email).addedBy(actor.getEmail()).build();
        }
        contact.setActive(true);
        if (StringUtils.hasText(name)) {
            contact.setName(name.strip());
        }
        contact = contactRepository.save(contact);
        auditService.record(actor, AuditAction.CLIENT_CONTACT_ADDED, "ClientContact", contact.getId(),
                Map.of("clientId", clientId));
        return ClientContactResponse.from(contact);
    }

    /** Access ends at once: the next request from their session is refused. */
    @Transactional
    public ClientContactResponse remove(AppUser actor, UUID contactId) {
        accessPolicy.require(actor, Capability.MANAGE_CLIENTS);
        ClientContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new NotFoundException("Contact not found"));
        if (contact.isActive()) {
            contact.setActive(false);
            auditService.record(actor, AuditAction.CLIENT_CONTACT_REMOVED, "ClientContact", contactId,
                    Map.of("clientId", contact.getClient().getId()));
        }
        return ClientContactResponse.from(contact);
    }

    private Client client(UUID id) {
        return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client not found"));
    }
}
