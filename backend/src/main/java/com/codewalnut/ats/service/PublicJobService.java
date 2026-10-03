package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationSource;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.TrackerDtos.MyApplicationResponse;
import com.codewalnut.ats.dto.TrackerDtos.PublicJobResponse;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Shareable job links: the public job page and candidates applying through it
 * (docs/features/job-links.md). Candidates only ever see their own applications, with a simple
 * status — never internal stages, notes or other candidates.
 */
@Service
@RequiredArgsConstructor
public class PublicJobService {

    static final String APPLIED_NOTE = "Applied via job link";
    static final String COMPANY = "CodeWalnut";

    private final JobOpeningRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final TrackerService trackerService;
    private final DocumentService documentService;
    private final AuditService auditService;
    private final MessageService messageService;

    @Transactional(readOnly = true)
    public PublicJobResponse publicJob(String slug) {
        JobOpening job = publishedJob(slug);
        return new PublicJobResponse(job.getPublicSlug(), job.getTitle(), COMPANY, job.getLocation(),
                job.getWorkMode() == null ? null : job.getWorkMode().getLabel(), job.getEmploymentType(),
                job.getDescription(), job.getStatus() == JobStatus.OPEN);
    }

    @Transactional
    public MyApplicationResponse apply(CandidateAccount account, String slug, String name, String phone, String note,
            boolean consent, MultipartFile resume) throws IOException {
        JobOpening job = publishedJob(slug);
        if (job.getStatus() != JobStatus.OPEN) {
            throw new IllegalArgumentException("This role is no longer accepting applications");
        }
        if (!consent) {
            throw new IllegalArgumentException("consent: please agree so we can keep your details for this application");
        }
        if (!StringUtils.hasText(name) || name.trim().length() > 200) {
            throw new IllegalArgumentException("name: please enter your full name");
        }
        String cleanPhone = StringUtils.hasText(phone) ? phone.replaceAll("[^0-9+]", "") : "";
        if (cleanPhone.replace("+", "").length() < 10 || cleanPhone.length() > 16) {
            throw new IllegalArgumentException("phone: please enter a valid phone number");
        }
        if (resume == null || resume.isEmpty()) {
            throw new IllegalArgumentException("file: please attach your résumé");
        }

        Candidate candidate = candidateRepository.findByEmail(account.getEmail())
                .orElseGet(() -> candidateRepository.save(Candidate.builder()
                        .name(name.trim().replaceAll("\\s+", " "))
                        .email(account.getEmail())
                        .phone(cleanPhone)
                        .build()));
        if (applicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId())) {
            throw new ConflictException("You have already applied for this role");
        }
        if (candidate.getPhone() == null) {
            candidate.setPhone(cleanPhone);
        }

        CandidateDocument document = documentService.store(candidate.getId(), DocumentKind.ORIGINAL_RESUME, resume,
                account.getEmail());
        String fullNote = StringUtils.hasText(note) ? APPLIED_NOTE + ": " + note.trim() : APPLIED_NOTE;
        Application application = trackerService.createApplication(account.getEmail(), job, candidate,
                Stage.SOURCED, fullNote.length() > 5000 ? fullNote.substring(0, 5000) : fullNote,
                ApplicationSource.JOB_LINK, Instant.now());
        documentService.resumeAdded(candidate.getId(), DocumentKind.ORIGINAL_RESUME);
        auditService.recordAnonymous(account.getEmail(), AuditAction.CANDIDATE_APPLIED,
                Map.of("jobId", job.getId(), "applicationId", application.getId(), "documentId", document.getId()));
        return toMine(application);
    }

    @Transactional(readOnly = true)
    public List<MyApplicationResponse> myApplications(CandidateAccount account) {
        return candidateRepository.findByEmail(account.getEmail())
                .map(c -> applicationRepository.findByCandidateIdOrderByCreatedAtDesc(c.getId()).stream()
                        .map(this::toMine)
                        .toList())
                .orElse(List.of());
    }

    private JobOpening publishedJob(String slug) {
        return jobRepository.findByPublicSlug(slug)
                .filter(JobOpening::isPublished)
                .orElseThrow(() -> new NotFoundException("This job link isn't active"));
    }

    private MyApplicationResponse toMine(Application a) {
        return new MyApplicationResponse(a.getId(), a.getJob().isPublished() ? a.getJob().getPublicSlug() : null,
                a.getJob().getTitle(), candidateStatus(a.getStage()), a.getCreatedAt(), messageService.unreadForCandidate(a));
    }

    /** What a candidate sees. Deliberately coarse. */
    static String candidateStatus(Stage stage) {
        return switch (stage) {
            case SOURCED -> "Applied";
            case SELECTED, OFFER_SENT, OFFER_ACCEPTED, JOINED -> "Selected";
            case REJECTED -> "Not progressing";
            case WITHDRAWN -> "Withdrawn";
            default -> "Under review";
        };
    }
}
