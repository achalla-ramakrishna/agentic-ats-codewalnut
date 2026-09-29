package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Upload, list and download candidate résumés. Only PDF and Word files, checked by content. */
@Service
@RequiredArgsConstructor
public class DocumentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final CandidateDocumentRepository documentRepository;
    private final CandidateRepository candidateRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CandidateDocumentRepository.Info> list(AppUser actor, UUID candidateId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        requireCandidate(candidateId);
        return documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId);
    }

    @Transactional
    public CandidateDocumentRepository.Info upload(AppUser actor, UUID candidateId, DocumentKind kind, MultipartFile file)
            throws IOException {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        requireCandidate(candidateId);
        CandidateDocument saved = store(candidateId, kind, file, actor.getEmail());
        auditService.record(actor, AuditAction.DOCUMENT_UPLOADED, "Candidate", candidateId,
                Map.of("kind", kind, "documentId", saved.getId(), "bytes", saved.getSizeBytes()));
        return info(candidateId, saved.getId());
    }

    /** Validates and saves a file. Callers do the permission check and auditing. */
    CandidateDocument store(UUID candidateId, DocumentKind kind, MultipartFile file, String uploadedBy)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file: please choose a file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("file: must be 10 MB or smaller");
        }
        String name = safeFileName(file.getOriginalFilename());
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String contentType = CONTENT_TYPES.get(extension);
        byte[] data = file.getBytes();
        if (contentType == null || !looksLike(extension, data)) {
            throw new IllegalArgumentException("file: only PDF or Word (.doc, .docx) files are accepted");
        }
        return documentRepository.save(CandidateDocument.builder()
                .candidateId(candidateId)
                .kind(kind)
                .fileName(name)
                .contentType(contentType)
                .sizeBytes(data.length)
                .data(data)
                .uploadedBy(uploadedBy)
                .build());
    }

    private CandidateDocumentRepository.Info info(UUID candidateId, UUID documentId) {
        return documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public CandidateDocument download(AppUser actor, UUID documentId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        CandidateDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        auditService.record(actor, AuditAction.DOCUMENT_DOWNLOADED, "Candidate", document.getCandidateId(),
                Map.of("documentId", documentId, "kind", document.getKind()));
        document.getData(); // load the bytes inside the transaction
        return document;
    }

    private void requireCandidate(UUID candidateId) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new NotFoundException("Candidate not found");
        }
    }

    /** Checks the file's first bytes match its extension, so a renamed executable is refused. */
    static boolean looksLike(String extension, byte[] data) {
        return switch (extension) {
            case "pdf" -> startsWith(data, new byte[] {'%', 'P', 'D', 'F'});
            case "docx" -> startsWith(data, new byte[] {'P', 'K', 3, 4});
            case "doc" -> startsWith(data, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    private static String safeFileName(String original) {
        String name = original == null ? "resume" : original.replaceAll("^.*[\\\\/]", "");
        name = name.replaceAll("[^A-Za-z0-9._() -]", "_").trim();
        return name.isEmpty() ? "resume" : name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
