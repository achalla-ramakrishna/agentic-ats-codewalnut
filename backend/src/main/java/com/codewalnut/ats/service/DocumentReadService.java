package com.codewalnut.ats.service;

import com.codewalnut.ats.client.ResumeAnalyzer;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.ClientShareRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes and captures document metadata; these short transactions never call object storage. */
@Service
@RequiredArgsConstructor
public class DocumentReadService {
    private final CandidateDocumentRepository documents;
    private final ClientShareRepository shares;
    private final ApplicationRepository applications;
    private final AccessPolicy accessPolicy;
    private final AuditService audit;
    private final DocumentContentService content;

    @Transactional(readOnly = true)
    public DocumentContentService.Source staff(AppUser actor, UUID documentId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        var document = documents.findById(documentId).orElseThrow(() -> new NotFoundException("File not found"));
        if (document.getKind().isSensitive()) accessPolicy.require(actor, Capability.VIEW_ID_DOCUMENTS);
        audit.record(actor, AuditAction.DOCUMENT_DOWNLOADED, "Candidate", document.getCandidateId(),
                Map.of("documentId", documentId, "kind", document.getKind()));
        return content.snapshot(document);
    }

    @Transactional(readOnly = true)
    public DocumentContentService.Source client(ClientContact contact, UUID documentId) {
        var share = shares.findByClientIdAndRevokedAtIsNullOrderBySharedAtDesc(contact.getClient().getId()).stream()
                .filter(s -> s.getDocumentIds().contains(documentId)).findFirst()
                .orElseThrow(() -> new NotFoundException("File not found"));
        var document = documents.findById(documentId).orElseThrow(() -> new NotFoundException("File not found"));
        audit.recordAnonymous(contact.getEmail(), AuditAction.DOCUMENT_DOWNLOADED,
                Map.of("clientId", contact.getClient().getId(), "applicationId", share.getApplication().getId(),
                        "documentId", documentId, "kind", document.getKind()));
        return content.snapshot(document);
    }

    public record ResumeSource(ResumeAnalyzer.Job job, DocumentContentService.Source document) {}

    @Transactional(readOnly = true)
    public ResumeSource forGeneration(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        var application = applications.findById(applicationId).orElseThrow(() -> new NotFoundException("Candidate entry not found"));
        var original = documents.findByCandidateIdOrderByUploadedAtDesc(application.getCandidate().getId()).stream()
                .filter(d -> d.getKind() == DocumentKind.ORIGINAL_RESUME).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Upload the candidate's original résumé first"));
        return new ResumeSource(new ResumeAnalyzer.Job(application.getJob().getTitle(), application.getJob().getDescription()),
                content.snapshot(documents.findById(original.getId()).orElseThrow()));
    }
}
