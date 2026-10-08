package com.codewalnut.ats.service;

import com.codewalnut.ats.client.BrandedResume;
import com.codewalnut.ats.client.ResumeAnalyzer;
import com.codewalnut.ats.client.ResumeWriter;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.CodeWalnutResume;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.dto.AssessmentDtos.InviteView;
import com.codewalnut.ats.dto.CodeWalnutResumeDtos.DraftResponse;
import com.codewalnut.ats.dto.CodeWalnutResumeDtos.SavedResponse;
import com.codewalnut.ats.dto.CodeWalnutResumeDtos.UpdateRequest;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CodeWalnutResumeRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * CodeWalnut-branded résumés for clients (ADR-0012): the AI rewrites the original résumé for the
 * opening, a person edits it, and the PDF is saved as the candidate's CodeWalnut résumé, ready to
 * share. Phone numbers are always removed, whatever the AI or the editor puts in.
 */
@Service
public class CodeWalnutResumeService {

    /** 10+ digits with optional +, spaces, dots, dashes or brackets: a phone number. */
    static final Pattern PHONE = Pattern.compile("\\+?\\(?\\d[\\d\\s().-]{8,}\\d");
    private static final Pattern LINK = Pattern.compile("[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}(/[A-Za-z0-9._~%/-]*)?");
    private static final Pattern URL = Pattern.compile("(?i)\\b(https?://|www\\.)\\S+|\\b(linkedin|github)\\.com/\\S*");

    private final ApplicationRepository applicationRepository;
    private final CandidateDocumentRepository documentRepository;
    private final CodeWalnutResumeRepository resumeRepository;
    private final DocumentService documentService;
    private final DocumentContentService content;
    private final DocumentReadService documentReads;
    private final TransactionTemplate generationSave;
    private final AssessmentInviteService tests;
    private final ResumeWriter writer;
    private final BrandedResumeRenderer renderer;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final String footer;

    public CodeWalnutResumeService(ApplicationRepository applicationRepository,
            CandidateDocumentRepository documentRepository, CodeWalnutResumeRepository resumeRepository,
            DocumentService documentService, DocumentContentService content, AssessmentInviteService tests, ResumeWriter writer,
            BrandedResumeRenderer renderer, AccessPolicy accessPolicy, AuditService auditService,
            ObjectMapper objectMapper, DocumentReadService documentReads, PlatformTransactionManager transactions,
            @Value("${ats.branding.resume-footer:Presented by Code Walnut | Staffing enquiries through Code Walnut}") String footer) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.resumeRepository = resumeRepository;
        this.documentService = documentService;
        this.content = content;
        this.documentReads = documentReads;
        this.generationSave = new TransactionTemplate(transactions);
        this.tests = tests;
        this.writer = writer;
        this.renderer = renderer;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.footer = footer;
    }

    @Transactional(readOnly = true)
    public DraftResponse get(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        Application application = application(applicationId);
        return resumeRepository.findByApplicationId(applicationId)
                .map(r -> response(application, r))
                .orElseGet(() -> new DraftResponse(false, writer.available(), null, true, true, screening(applicationId),
                        null, null, null, null));
    }

    /** Asks the AI for a fresh draft from the latest original résumé (replaces the current draft). */
    public DraftResponse generate(AppUser actor, UUID applicationId) {
        var source = documentReads.forGeneration(actor, applicationId);
        var document = source.document();
        BrandedResume draft = writer.write(source.job(),
                new ResumeAnalyzer.ResumeFile(document.fileName(), document.contentType(), content.read(document)));
        return generationSave.execute(status -> {
            accessPolicy.require(actor, Capability.MANAGE_JOBS);
            Application application = application(applicationId);
            var latest = documentRepository.findByCandidateIdOrderByUploadedAtDesc(application.getCandidate().getId()).stream()
                    .filter(d -> d.getKind() == DocumentKind.ORIGINAL_RESUME).findFirst();
            if (latest.isEmpty() || !latest.get().getId().equals(document.documentId())) {
                throw new ConflictException("The original résumé changed while drafting. Generate the draft again.");
            }
            CodeWalnutResume row = resumeRepository.findByApplicationId(applicationId)
                    .orElseGet(() -> CodeWalnutResume.builder().applicationId(applicationId).showEmail(true).includeScreening(true).build());
            BrandedResume clean = clean(draft, application);
            row.setData(write(clean));
            row.setSourceDocumentId(document.documentId());
            row.setModel(writer.model());
            row.setUpdatedBy(actor.getEmail());
            resumeRepository.save(row);
            auditService.record(actor, AuditAction.CODEWALNUT_RESUME_UPDATED, "Application", applicationId, Map.of("via", "ai"));
            return response(application, row);
        });
    }

    @Transactional
    public DraftResponse update(AppUser actor, UUID applicationId, UpdateRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        CodeWalnutResume row = resumeRepository.findByApplicationId(applicationId)
                .orElseGet(() -> CodeWalnutResume.builder().applicationId(applicationId).build());
        row.setData(write(clean(request.resume(), application)));
        row.setShowEmail(request.showEmail());
        row.setIncludeScreening(request.includeScreening());
        row.setUpdatedBy(actor.getEmail());
        row.setUpdatedAt(java.time.Instant.now());
        resumeRepository.save(row);
        auditService.record(actor, AuditAction.CODEWALNUT_RESUME_UPDATED, "Application", applicationId, Map.of("via", "edit"));
        return response(application, row);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        CodeWalnutResume row = existing(applicationId);
        return renderer.pdf(read(row), options(row));
    }

    @Transactional(readOnly = true)
    public byte[] docx(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        CodeWalnutResume row = existing(applicationId);
        return renderer.docx(read(row), options(row));
    }

    /** Stores the PDF as the candidate's CodeWalnut résumé (a new version), ready to share with the client. */
    @Transactional
    public SavedResponse save(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        CodeWalnutResume row = existing(applicationId);
        if (!StringUtils.hasText(read(row).email())) {
            throw new IllegalArgumentException("email: add the candidate's email to the résumé first. CodeWalnut résumés always show it");
        }
        String fileName = fileName(application.getCandidate().getName()) + ".pdf";
        CandidateDocument document = documentService.store(application.getCandidate().getId(), DocumentKind.CODEWALNUT_RESUME,
                fileName, renderer.pdf(read(row), options(row)), actor.getEmail());
        row.setSavedDocumentId(document.getId());
        auditService.record(actor, AuditAction.DOCUMENT_UPLOADED, "Candidate", application.getCandidate().getId(),
                Map.of("kind", DocumentKind.CODEWALNUT_RESUME, "documentId", document.getId(), "generated", true));
        return new SavedResponse(document.getId(), document.getFileName());
    }

    public String fileNameFor(UUID applicationId) {
        return applicationRepository.findById(applicationId).map(a -> fileName(a.getCandidate().getName())).orElse("CodeWalnut résumé");
    }

    // ---- helpers ----

    /** Removes phone numbers and links everywhere, and fills a missing name from the candidate. */
    BrandedResume clean(BrandedResume r, Application application) {
        String name = StringUtils.hasText(r.name()) ? strip(r.name()) : application.getCandidate().getName();
        String email = StringUtils.hasText(r.email()) && r.email().contains("@") ? r.email().strip()
                : Objects.requireNonNullElse(application.getCandidate().getEmail(), "");
        List<BrandedResume.SkillGroup> skills = r.skills() == null ? List.of() : r.skills().stream()
                .filter(Objects::nonNull)
                .map(g -> new BrandedResume.SkillGroup(strip(g.label()),
                        g.items() == null ? List.of() : g.items().stream().map(CodeWalnutResumeService::strip).filter(StringUtils::hasText).toList()))
                .toList();
        List<BrandedResume.Section> sections = r.sections() == null ? List.of() : r.sections().stream()
                .filter(Objects::nonNull)
                .map(s -> new BrandedResume.Section(strip(s.title()), s.entries() == null ? List.of() : s.entries().stream()
                        .filter(Objects::nonNull)
                        .map(e -> new BrandedResume.Entry(strip(e.title()), strip(e.subtitle()), strip(e.period()),
                                e.bullets() == null ? List.of() : e.bullets().stream().map(CodeWalnutResumeService::strip)
                                        .filter(StringUtils::hasText).toList()))
                        .toList()))
                .toList();
        return new BrandedResume(name, strip(r.headline()), strip(r.location()), email, link(r.link()), strip(r.summary()), skills, sections);
    }

    /** A GitHub or portfolio link as "github.com/asha" (no scheme, www or trailing slash); LinkedIn and anything else odd is dropped. */
    static String link(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String l = raw.strip().replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "").replaceAll("/+$", "");
        if (!LINK.matcher(l).matches() || l.toLowerCase(java.util.Locale.ROOT).matches("^(\\S*\\.)?(linkedin|facebook|instagram|twitter|x|wa|t)\\.(com|me)(/.*)?$")) {
            return "";
        }
        return l;
    }

    static String strip(String s) {
        if (s == null) {
            return "";
        }
        // Only runs of 10+ digits count as phone numbers, so "2022 - 2026" stays.
        String out = PHONE.matcher(s).replaceAll(m -> m.group().replaceAll("\\D", "").length() >= 10 ? "" : java.util.regex.Matcher.quoteReplacement(m.group()))
                .replaceAll(URL.pattern(), "");
        out = out.replaceAll("(?i)\\b(phone|mobile|mob|tel|ph)\\s*[:.]?\\s*(\\||,)?", "");
        return out.replaceAll("[ \\t]{2,}", " ").replaceAll("\\s*\\|\\s*\\|\\s*", " | ").replaceAll("^[\\s|,]+|[\\s|,]+$", "").strip();
    }

    private DraftResponse response(Application application, CodeWalnutResume row) {
        String source = row.getSourceDocumentId() == null ? null
                : documentRepository.findByCandidateIdOrderByUploadedAtDesc(application.getCandidate().getId()).stream()
                        .filter(d -> d.getId().equals(row.getSourceDocumentId()))
                        .map(CandidateDocumentRepository.Info::getFileName).findFirst().orElse(null);
        return new DraftResponse(true, writer.available(), read(row), row.isShowEmail(), row.isIncludeScreening(),
                screening(application.getId()), source, row.getSavedDocumentId(), row.getUpdatedBy(), row.getUpdatedAt());
    }

    /** Passed tests, as lines for the "CodeWalnut screening" section. */
    List<String> screening(UUID applicationId) {
        return tests.latestSubmitted(List.of(applicationId)).stream()
                .filter(t -> Boolean.TRUE.equals(t.passed()))
                .map(CodeWalnutResumeService::screeningLine)
                .distinct()
                .toList();
    }

    private static String screeningLine(InviteView t) {
        return t.title() + " test: " + t.percent() + "% (pass mark " + t.passPercent() + "%)";
    }

    private BrandedResumeRenderer.Options options(CodeWalnutResume row) {
        // The email is always on CodeWalnut résumés (the old "hide email" option is ignored).
        return new BrandedResumeRenderer.Options(true,
                row.isIncludeScreening() ? screening(row.getApplicationId()) : List.of(), footer);
    }

    private CodeWalnutResume existing(UUID applicationId) {
        application(applicationId);
        return resumeRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new NotFoundException("No CodeWalnut résumé yet. Create one first."));
    }

    private String write(BrandedResume r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private BrandedResume read(CodeWalnutResume row) {
        try {
            return objectMapper.readValue(row.getData(), BrandedResume.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fileName(String name) {
        String safe = name == null ? "Candidate" : name.replaceAll("[^A-Za-z0-9 ._-]", "").strip();
        return (safe.isEmpty() ? "Candidate" : safe) + " - CodeWalnut";
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Candidate entry not found"));
    }
}
