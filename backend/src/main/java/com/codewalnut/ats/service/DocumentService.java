package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.DocumentRequest;
import com.codewalnut.ats.dto.ProfileDtos.DocumentRequestResponse;
import com.codewalnut.ats.dto.ProfileDtos.MyDocument;
import com.codewalnut.ats.dto.ProfileDtos.MyDocumentsResponse;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.DocumentRequestRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Candidate documents: résumés, and background-verification documents (Aadhaar, PAN, degree,
 * photo). Files are checked by content, not just by name. Government IDs are visible only with
 * VIEW_ID_DOCUMENTS. Candidates upload their own from their candidate page when asked.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private final CandidateDocumentRepository documentRepository;
    private final CandidateRepository candidateRepository;
    private final DocumentRequestRepository requestRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    // ---- staff ----

    @Transactional(readOnly = true)
    public List<CandidateDocumentRepository.Info> list(AppUser actor, UUID candidateId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        requireCandidate(candidateId);
        boolean ids = accessPolicy.has(actor, Capability.VIEW_ID_DOCUMENTS);
        return documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .filter(d -> ids || !d.getKind().isSensitive())
                .toList();
    }

    @Transactional
    public CandidateDocumentRepository.Info upload(AppUser actor, UUID candidateId, DocumentKind kind, MultipartFile file)
            throws IOException {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        if (kind.isSensitive()) {
            accessPolicy.require(actor, Capability.VIEW_ID_DOCUMENTS);
        }
        requireCandidate(candidateId);
        CandidateDocument saved = store(candidateId, kind, file, actor.getEmail());
        fulfil(candidateId, kind);
        resumeAdded(candidateId, kind);
        auditService.record(actor, AuditAction.DOCUMENT_UPLOADED, "Candidate", candidateId,
                Map.of("kind", kind, "documentId", saved.getId(), "bytes", saved.getSizeBytes()));
        return info(candidateId, saved.getId());
    }

    @Transactional(readOnly = true)
    public CandidateDocument download(AppUser actor, UUID documentId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        CandidateDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("File not found"));
        if (document.getKind().isSensitive()) {
            accessPolicy.require(actor, Capability.VIEW_ID_DOCUMENTS);
        }
        auditService.record(actor, AuditAction.DOCUMENT_DOWNLOADED, "Candidate", document.getCandidateId(),
                Map.of("documentId", documentId, "kind", document.getKind()));
        document.getData(); // load the bytes inside the transaction
        return document;
    }

    /** Ask the candidate to upload documents from their candidate page. Already-open requests are kept. */
    @Transactional
    public List<DocumentRequestResponse> request(AppUser actor, UUID candidateId, List<DocumentKind> kinds) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        requireCandidate(candidateId);
        Set<DocumentKind> open = requestRepository.findByCandidateIdAndFulfilledAtIsNull(candidateId).stream()
                .map(DocumentRequest::getKind).collect(Collectors.toSet());
        List<DocumentKind> added = new ArrayList<>();
        for (DocumentKind kind : new LinkedHashSet<>(kinds)) {
            if (!kind.isCandidateUploadable()) {
                throw new IllegalArgumentException("kinds: candidates can't upload the " + kind.getLabel());
            }
            if (!open.contains(kind)) {
                requestRepository.save(DocumentRequest.builder()
                        .candidateId(candidateId).kind(kind).requestedBy(actor.getEmail()).build());
                added.add(kind);
            }
        }
        if (!added.isEmpty()) {
            String labels = added.stream().map(DocumentKind::getLabel).collect(Collectors.joining(", "));
            history(candidateId, ApplicationEventType.DOCS_REQUESTED, "Requested from the candidate: " + labels,
                    actor.getEmail());
            auditService.record(actor, AuditAction.DOCUMENTS_REQUESTED, "Candidate", candidateId, Map.of("kinds", added));
        }
        return requests(actor, candidateId);
    }

    @Transactional(readOnly = true)
    public List<DocumentRequestResponse> requests(AppUser actor, UUID candidateId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        return requestRepository.findByCandidateIdOrderByRequestedAtDesc(candidateId).stream()
                .map(DocumentRequestResponse::from).toList();
    }

    // ---- candidate ----

    @Transactional(readOnly = true)
    public MyDocumentsResponse myDocuments(CandidateAccount account) {
        Candidate candidate = mine(account);
        List<MyDocument> documents = documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidate.getId()).stream()
                .filter(d -> d.getKind().isCandidateUploadable())
                .map(d -> new MyDocument(d.getId(), d.getKind(), d.getKind().getLabel(), d.getFileName(), d.getUploadedAt()))
                .toList();
        List<DocumentRequestResponse> open = requestRepository.findByCandidateIdAndFulfilledAtIsNull(candidate.getId())
                .stream().map(DocumentRequestResponse::from).toList();
        return new MyDocumentsResponse(documents, open);
    }

    @Transactional
    public MyDocument candidateUpload(CandidateAccount account, DocumentKind kind, MultipartFile file) throws IOException {
        if (!kind.isCandidateUploadable()) {
            throw new IllegalArgumentException("kind: this document is added by CodeWalnut");
        }
        Candidate candidate = mine(account);
        CandidateDocument saved = store(candidate.getId(), kind, file, account.getEmail());
        fulfil(candidate.getId(), kind);
        resumeAdded(candidate.getId(), kind);
        history(candidate.getId(), ApplicationEventType.DOC_UPLOADED, "Candidate uploaded: " + kind.getLabel(),
                account.getEmail());
        auditService.recordAnonymous(account.getEmail(), AuditAction.DOCUMENT_UPLOADED,
                Map.of("candidateId", candidate.getId(), "kind", kind, "documentId", saved.getId()));
        return new MyDocument(saved.getId(), kind, kind.getLabel(), saved.getFileName(), saved.getUploadedAt());
    }

    // ---- shared ----

    /** Validates and saves a file. Callers do the permission check and auditing. */
    CandidateDocument store(UUID candidateId, DocumentKind kind, MultipartFile file, String uploadedBy)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file: please choose a file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("file: must be 10 MB or smaller");
        }
        return store(candidateId, kind, file.getOriginalFilename(), file.getBytes(), uploadedBy);
    }

    /** As above, for a file already in memory (e.g. a résumé from a bulk upload). */
    CandidateDocument store(UUID candidateId, DocumentKind kind, String originalName, byte[] data, String uploadedBy) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("file: please choose a file");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("file: must be 10 MB or smaller");
        }
        String name = safeFileName(originalName);
        String extension = extension(name);
        boolean allowed = DOCUMENT_EXTENSIONS.contains(extension)
                || (kind.isImagesAllowed() && IMAGE_EXTENSIONS.contains(extension));
        if (!allowed || !looksLike(extension, data)) {
            throw new IllegalArgumentException(kind.isImagesAllowed()
                    ? "file: only PDF, Word, JPG or PNG files are accepted"
                    : "file: only PDF or Word (.doc, .docx) files are accepted");
        }
        return documentRepository.save(CandidateDocument.builder()
                .candidateId(candidateId)
                .kind(kind)
                .fileName(name)
                .contentType(CONTENT_TYPES.get(extension))
                .sizeBytes(data.length)
                .data(data)
                .uploadedBy(uploadedBy)
                .build());
    }

    /** A new original résumé: its readings get refreshed after this transaction commits (ADR-0010). */
    void resumeAdded(UUID candidateId, DocumentKind kind) {
        if (kind == DocumentKind.ORIGINAL_RESUME) {
            events.publishEvent(new ResumeAddedEvent(candidateId));
        }
    }

    private void fulfil(UUID candidateId, DocumentKind kind) {
        Instant now = Instant.now();
        requestRepository.findByCandidateIdAndFulfilledAtIsNull(candidateId).stream()
                .filter(r -> r.getKind() == kind)
                .forEach(r -> r.setFulfilledAt(now));
    }

    private void history(UUID candidateId, ApplicationEventType type, String note, String actorEmail) {
        for (Application application : applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)) {
            application.setUpdatedAt(Instant.now());
            eventRepository.save(ApplicationEvent.builder()
                    .application(application).type(type).note(note).actorEmail(actorEmail).build());
        }
    }

    private Candidate mine(CandidateAccount account) {
        return candidateRepository.findByEmail(account.getEmail().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("No application yet. Apply using a job link first."));
    }

    private CandidateDocumentRepository.Info info(UUID candidateId, UUID documentId) {
        return documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow();
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
            case "jpg", "jpeg" -> startsWith(data, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "png" -> startsWith(data, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    /** The lower-case extension of a file name, or "". */
    static String extension(String name) {
        return name != null && name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    }

    static String contentType(String extension) {
        return CONTENT_TYPES.get(extension);
    }

    static String safeFileName(String original) {
        String name = original == null ? "document" : original.replaceAll("^.*[\\\\/]", "");
        name = name.replaceAll("[^A-Za-z0-9._() -]", "_").trim();
        return name.isEmpty() ? "document" : name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
