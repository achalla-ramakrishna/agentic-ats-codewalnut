package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.dto.DocumentDownload;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.ClientShare;
import com.codewalnut.ats.dto.ClientDtos.ClientCandidate;
import com.codewalnut.ats.dto.ClientDtos.ClientMe;
import com.codewalnut.ats.dto.ClientDtos.SharedDocument;
import com.codewalnut.ats.dto.ClientDtos.SharedProfile;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.ClientShareRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a signed-in client contact sees: only active shares for their own company, and only the
 * parts CodeWalnut ticked. Anything else is "not found", so nothing leaks across clients.
 */
@Service
@RequiredArgsConstructor
public class ClientPortalService {

    private final ClientShareRepository shareRepository;
    private final CandidateDocumentRepository documentRepository;
    private final DocumentContentService content;
    private final DocumentReadService documentReads;
    private final AuditService audit;

    public ClientMe me(ClientContact contact) {
        return new ClientMe(contact.getEmail(), contact.getName(), contact.getClient().getName());
    }

    @Transactional
    public List<ClientCandidate> candidates(ClientContact contact) {
        Instant now = Instant.now();
        return shareRepository.findByClientIdAndRevokedAtIsNullOrderBySharedAtDesc(contact.getClient().getId()).stream()
                .map(share -> {
                    share.setLastViewedAt(now);
                    return view(share);
                })
                .toList();
    }

    public DocumentDownload download(ClientContact contact, UUID documentId) {
        var download = documentReads.client(contact, documentId);
        audit.recordAnonymous(contact.getEmail(), AuditAction.DOCUMENT_DOWNLOADED, download.auditDetails());
        var source = download.document();
        return new DocumentDownload(source.fileName(), source.contentType(), content.read(source));
    }

    /** The application, if it is actively shared with this contact's company; otherwise "not found". */
    @Transactional(readOnly = true)
    public Application sharedApplication(ClientContact contact, UUID applicationId) {
        return shareRepository.findByApplicationId(applicationId)
                .filter(ClientShare::isActive)
                .filter(s -> s.getClient().getId().equals(contact.getClient().getId()))
                .map(ClientShare::getApplication)
                .orElseThrow(() -> new NotFoundException("Candidate not found"));
    }

    private ClientCandidate view(ClientShare share) {
        Application application = share.getApplication();
        Candidate candidate = application.getCandidate();
        List<SharedDocument> documents = documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidate.getId())
                .stream()
                .filter(d -> share.getDocumentIds().contains(d.getId()))
                .sorted(Comparator.comparing(d -> d.getKind().ordinal()))
                .map(d -> new SharedDocument(d.getId(), d.getKind(), d.getKind().getLabel(), d.getFileName(),
                        d.getUploadedAt()))
                .toList();
        return new ClientCandidate(application.getId(), candidate.getName(), application.getJob().getTitle(),
                application.getStage().getLabel(), share.getSharedAt(), share.getNote(),
                share.isIncludeContact() ? candidate.getEmail() : null,
                share.isIncludeContact() ? candidate.getPhone() : null,
                share.isIncludeProfile() ? SharedProfile.from(candidate) : null,
                documents);
    }
}
