package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.Client;
import com.codewalnut.ats.domain.ClientShare;
import com.codewalnut.ats.dto.ClientDtos.ShareRequest;
import com.codewalnut.ats.dto.ClientDtos.ShareResponse;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.ClientShareRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Staff choose exactly what a client sees about a candidate: always name, opening and stage;
 * contact details, profile and particular document versions only when ticked. Revocable, and
 * recorded in the history and audit log. The only path by which candidate data reaches a client.
 */
@Service
@RequiredArgsConstructor
public class ClientShareService {

    private final ApplicationRepository applicationRepository;
    private final ClientShareRepository shareRepository;
    private final CandidateDocumentRepository documentRepository;
    private final ApplicationEventRepository eventRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ShareResponse get(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        Application application = application(applicationId);
        Client client = client(application);
        return shareRepository.findByApplicationId(applicationId)
                .map(ShareResponse::from)
                .orElse(ShareResponse.none(applicationId, client.getName()));
    }

    @Transactional
    public ShareResponse share(AppUser actor, UUID applicationId, ShareRequest request) {
        accessPolicy.require(actor, Capability.SHARE_WITH_CLIENTS);
        Application application = application(applicationId);
        Client client = client(application);
        Set<UUID> documentIds = new LinkedHashSet<>(request.documentIds());
        List<String> labels = new ArrayList<>();
        if (request.includeContact()) {
            labels.add("contact details");
        }
        if (request.includeProfile()) {
            labels.add("profile");
        }
        for (UUID documentId : documentIds) {
            CandidateDocument document = documentRepository.findById(documentId)
                    .filter(d -> d.getCandidateId().equals(application.getCandidate().getId()))
                    .orElseThrow(() -> new IllegalArgumentException("documentIds: not this candidate's document"));
            if (document.getKind().isSensitive()) {
                accessPolicy.require(actor, Capability.VIEW_ID_DOCUMENTS);
            }
            labels.add(document.getKind().getLabel());
        }
        ClientShare share = shareRepository.findByApplicationId(applicationId)
                .orElseGet(() -> ClientShare.builder().application(application).build());
        share.setClient(client);
        share.setIncludeContact(request.includeContact());
        share.setIncludeProfile(request.includeProfile());
        share.getDocumentIds().clear();
        share.getDocumentIds().addAll(documentIds);
        share.setNote(StringUtils.hasText(request.note()) ? request.note().strip() : null);
        share.setSharedBy(actor.getEmail());
        share.setSharedAt(Instant.now());
        share.setRevokedAt(null);
        share = shareRepository.save(share);
        history(application, actor, "Shared with " + client.getName() + ": name, opening and stage"
                + (labels.isEmpty() ? "" : ", " + String.join(", ", labels)));
        auditService.record(actor, AuditAction.CLIENT_SHARE_UPDATED, "Application", applicationId, Map.of(
                "clientId", client.getId(), "contact", request.includeContact(), "profile", request.includeProfile(),
                "documentIds", List.copyOf(documentIds)));
        return ShareResponse.from(share);
    }

    @Transactional
    public ShareResponse revoke(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.SHARE_WITH_CLIENTS);
        Application application = application(applicationId);
        ClientShare share = shareRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new NotFoundException("Not shared with the client"));
        if (share.isActive()) {
            share.setRevokedAt(Instant.now());
            history(application, actor, "Stopped sharing with " + share.getClient().getName());
            auditService.record(actor, AuditAction.CLIENT_SHARE_REVOKED, "Application", applicationId,
                    Map.of("clientId", share.getClient().getId()));
        }
        return ShareResponse.from(share);
    }

    private void history(Application application, AppUser actor, String note) {
        application.setUpdatedAt(Instant.now());
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.SHARED_WITH_CLIENT)
                .note(note)
                .actorEmail(actor.getEmail())
                .build());
    }

    private static Client client(Application application) {
        Client client = application.getJob().getClient();
        if (client == null) {
            throw new IllegalArgumentException("This opening is internal, so there is no client to share with");
        }
        return client;
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Application not found"));
    }
}
