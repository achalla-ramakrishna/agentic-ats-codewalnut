package com.codewalnut.ats.service;

import com.codewalnut.ats.client.ResumeAnalyzer;
import com.codewalnut.ats.client.ResumeInsight;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.ApplicationSource;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.CandidateInsight;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.ResumeIntake;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CandidateInsightRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.repository.ResumeIntakeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The database steps around reading a résumé. Each step is its own short transaction
 * (REQUIRES_NEW, as they also run right after a request commits); the slow AI call happens
 * between steps, outside any transaction.
 */
@Component
@RequiredArgsConstructor
class ResumeProcessor {

    static final String UPLOAD_NOTE = "Added from a résumé upload";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ResumeIntakeRepository intakeRepository;
    private final CandidateInsightRepository insightRepository;
    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final JobOpeningRepository jobRepository;
    private final DocumentService documentService;
    private final DocumentContentService content;
    private final TrackerService trackerService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    record Work(ResumeAnalyzer.Job job, ResumeAnalyzer.ResumeFile file, UUID documentId, String jobHash) {}
    record InsightSource(ResumeAnalyzer.Job job, DocumentContentService.Source document, String jobHash) {}

    Work readInsight(InsightSource source) {
        var document = source.document();
        return new Work(source.job(), new ResumeAnalyzer.ResumeFile(document.fileName(), document.contentType(), content.read(document)),
                document.documentId(), source.jobHash());
    }

    // ---- bulk upload ----

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Work> startIntake(UUID intakeId) {
        ResumeIntake intake = intakeRepository.findById(intakeId).orElse(null);
        if (intake == null || intake.getStatus() != ResumeIntake.Status.PENDING) {
            return Optional.empty();
        }
        JobOpening job = jobRepository.findById(intake.getJobId()).orElseThrow();
        return Optional.of(new Work(jobOf(job),
                new ResumeAnalyzer.ResumeFile(intake.getFileName(), intake.getContentType(), content.read(intake)),
                null, jobHash(job)));
    }

    /**
     * Finds the person (email, then phone, then the same name already in this opening) or adds
     * them, puts them in the opening at Applied / Sourced, attaches the résumé and saves the reading.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishIntake(UUID intakeId, ResumeInsight insight, String model, String jobHash) {
        ResumeIntake intake = intakeRepository.findById(intakeId).orElseThrow();
        if (intake.getStatus() != ResumeIntake.Status.PENDING) {
            return;
        }
        JobOpening job = jobRepository.findById(intake.getJobId()).orElseThrow();
        String email = clean(insight.email()) == null ? null : insight.email().strip().toLowerCase(Locale.ROOT);
        if (email != null && (!EMAIL.matcher(email).matches() || email.length() > 254)) {
            email = null;
        }
        String phone = clean(insight.phone()) == null ? null : insight.phone().replaceAll("[^0-9+]", "");
        if (phone != null && (phone.replace("+", "").length() < 10 || phone.length() > 16)) {
            phone = null;
        }
        String name = clean(insight.name());
        if (name == null) {
            name = nameFromFile(intake.getFileName());
        }
        name = name.replaceAll("\\s+", " ");
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }

        Optional<Candidate> existing = Optional.empty();
        if (email != null) {
            existing = candidateRepository.findByEmail(email);
        }
        if (existing.isEmpty() && phone != null) {
            existing = candidateRepository.findByPhone(phone).stream().findFirst();
        }
        Application application = null;
        if (existing.isPresent()) {
            UUID candidateId = existing.get().getId();
            application = applicationRepository.findByJobIdOrderByCandidateNameAsc(job.getId()).stream()
                    .filter(a -> a.getCandidate().getId().equals(candidateId)).findFirst().orElse(null);
        } else if (email == null && phone == null) {
            String sameName = name;
            application = applicationRepository.findByJobIdOrderByCandidateNameAsc(job.getId()).stream()
                    .filter(a -> a.getCandidate().getName().equalsIgnoreCase(sameName)).findFirst().orElse(null);
        }

        ResumeIntake.Outcome outcome;
        Candidate candidate;
        if (application != null) {
            outcome = ResumeIntake.Outcome.ALREADY_IN_OPENING;
            candidate = application.getCandidate();
        } else {
            if (job.getStatus() == JobStatus.CLOSED) {
                fail(intake, "This opening was closed before the résumé was read; reopen it and upload again.");
                return;
            }
            outcome = existing.isPresent() ? ResumeIntake.Outcome.EXISTING_CANDIDATE : ResumeIntake.Outcome.NEW_CANDIDATE;
            if (existing.isPresent()) {
                candidate = existing.get();
                if (candidate.getPhone() == null && phone != null) {
                    candidate.setPhone(phone);
                }
            } else {
                candidate = candidateRepository.save(Candidate.builder()
                        .name(name).email(email).phone(phone)
                        .graduationYear(insight.graduationYear() != null && insight.graduationYear() > 1950
                                ? insight.graduationYear() : null)
                        .build());
            }
            application = trackerService.createApplication(intake.getUploadedBy(), job, candidate, Stage.SOURCED,
                    UPLOAD_NOTE + " (" + intake.getFileName() + ")", ApplicationSource.RESUME_UPLOAD, null);
        }

        UUID documentId = sameResume(candidate.getId(), intake)
                .orElseGet(() -> documentService.store(candidate.getId(), DocumentKind.ORIGINAL_RESUME,
                        intake.getFileName(), content.read(intake), intake.getUploadedBy()).getId());
        saveDone(application.getId(), documentId, insight, model, jobHash);

        intake.setStatus(ResumeIntake.Status.DONE);
        intake.setOutcome(outcome);
        intake.setApplicationId(application.getId());
        intake.setData(null);
        intake.setProcessedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failIntake(UUID intakeId, String error) {
        intakeRepository.findById(intakeId).ifPresent(intake -> fail(intake, error));
    }

    private void fail(ResumeIntake intake, String error) {
        intake.setStatus(ResumeIntake.Status.FAILED);
        intake.setError(truncate(error, 500));
        intake.setData(null);
        intake.setProcessedAt(Instant.now());
    }

    /** The same file (name and size) already attached to this candidate: don't store it twice. */
    private Optional<UUID> sameResume(UUID candidateId, ResumeIntake intake) {
        String name = DocumentService.safeFileName(intake.getFileName());
        long size = content.read(intake).length;
        return documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .filter(d -> d.getKind() == DocumentKind.ORIGINAL_RESUME)
                .filter(d -> d.getFileName().equals(name) && d.getSizeBytes() == size)
                .map(CandidateDocumentRepository.Info::getId)
                .findFirst();
    }

    // ---- reading a candidate's latest résumé ----

    /** Marks the reading as in progress and loads the latest original résumé; empty when there is none. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<InsightSource> startInsight(UUID applicationId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return Optional.empty();
        }
        CandidateInsight insight = insightRepository.findByApplicationId(applicationId)
                .orElseGet(() -> CandidateInsight.builder().applicationId(applicationId).build());
        Optional<CandidateDocumentRepository.Info> latest = latestResume(application.getCandidate().getId());
        if (latest.isEmpty()) {
            insight.setStatus(CandidateInsight.Status.NO_RESUME);
            insight.setError(null);
            insightRepository.save(insight);
            return Optional.empty();
        }
        insight.setStatus(CandidateInsight.Status.PENDING);
        insight.setError(null);
        insightRepository.save(insight);
        CandidateDocument document = documentRepository.findById(latest.get().getId()).orElseThrow();
        return Optional.of(new InsightSource(jobOf(application.getJob()), content.snapshot(document), jobHash(application.getJob())));
    }

    /** Marks the reading as queued, so people see "Reading…" straight away. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPending(UUID applicationId) {
        CandidateInsight insight = insightRepository.findByApplicationId(applicationId)
                .orElseGet(() -> CandidateInsight.builder().applicationId(applicationId).build());
        insight.setStatus(CandidateInsight.Status.PENDING);
        insight.setError(null);
        insightRepository.save(insight);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishInsight(UUID applicationId, UUID documentId, ResumeInsight insight, String model, String jobHash) {
        saveDone(applicationId, documentId, insight, model, jobHash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInsight(UUID applicationId, String error) {
        insightRepository.findByApplicationId(applicationId).ifPresent(insight -> {
            insight.setStatus(CandidateInsight.Status.FAILED);
            insight.setError(truncate(error, 500));
        });
    }

    Optional<CandidateDocumentRepository.Info> latestResume(UUID candidateId) {
        return documentRepository.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .filter(d -> d.getKind() == DocumentKind.ORIGINAL_RESUME)
                .findFirst();
    }

    private void saveDone(UUID applicationId, UUID documentId, ResumeInsight insight, String model, String jobHash) {
        CandidateInsight row = insightRepository.findByApplicationId(applicationId)
                .orElseGet(() -> CandidateInsight.builder().applicationId(applicationId).build());
        row.setStatus(CandidateInsight.Status.DONE);
        row.setDocumentId(documentId);
        row.setFitPercent(fitPercent(insight.requirements()));
        row.setHeadline(truncate(clean(insight.headline()), 300));
        try {
            row.setData(objectMapper.writeValueAsString(insight));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        row.setError(null);
        row.setModel(truncate(model, 100));
        row.setJobHash(jobHash);
        row.setAnalyzedAt(Instant.now());
        insightRepository.save(row);
        applicationRepository.findById(applicationId).ifPresent(a -> fillProfile(a.getCandidate(), insight));
    }

    /**
     * Fills empty profile fields from the résumé. Never overwrites what a person (or the candidate)
     * entered, and never takes date of birth or other personal details.
     */
    void fillProfile(Candidate candidate, ResumeInsight insight) {
        List<String> filled = new java.util.ArrayList<>();
        String college = clean(insight.college());
        if (candidate.getCollege() == null && college != null) {
            candidate.setCollege(truncate(college, 200));
            filled.add("college");
        }
        String degree = clean(insight.degree());
        if (candidate.getDegree() == null && degree != null) {
            candidate.setDegree(truncate(degree, 200));
            filled.add("degree");
        }
        Integer year = insight.graduationYear();
        if (candidate.getGraduationYear() == null && year != null && year > 1950 && year < 2100) {
            candidate.setGraduationYear(year);
            filled.add("graduationYear");
        }
        String linkedin = linkedin(insight.linkedinUrl());
        if (candidate.getLinkedinUrl() == null && linkedin != null) {
            candidate.setLinkedinUrl(linkedin);
            filled.add("linkedinUrl");
        }
        String address = clean(insight.address());
        if (candidate.getCurrentAddress() == null && address != null) {
            candidate.setCurrentAddress(truncate(address.replaceAll("\\s+", " "), 1000));
            filled.add("currentAddress");
        }
        String email = clean(insight.email()) == null ? null : insight.email().strip().toLowerCase(Locale.ROOT);
        if (candidate.getEmail() == null && email != null && EMAIL.matcher(email).matches() && email.length() <= 254
                && candidateRepository.findByEmail(email).isEmpty()) {
            candidate.setEmail(email);
            filled.add("email");
        }
        String phone = clean(insight.phone()) == null ? null : insight.phone().replaceAll("[^0-9+]", "");
        if (candidate.getPhone() == null && phone != null && phone.replace("+", "").length() >= 10 && phone.length() <= 16) {
            candidate.setPhone(phone);
            filled.add("phone");
        }
        if (!filled.isEmpty()) {
            auditService.recordAnonymous("system:resume-reading", AuditAction.CANDIDATE_PROFILE_UPDATED,
                    java.util.Map.of("candidateId", candidate.getId(), "fields", filled));
        }
    }

    /** A LinkedIn profile link as a full https:// URL, or null. */
    static String linkedin(String raw) {
        String s = clean(raw);
        if (s == null || !s.toLowerCase(Locale.ROOT).contains("linkedin.com/")) {
            return null;
        }
        s = s.replaceAll("\\s+", "");
        if (!s.startsWith("http")) {
            s = "https://" + s.replaceFirst("^/+", "");
        }
        return s.length() > 300 ? null : s;
    }

    /** MET counts 2, PARTIAL 1, NOT_EVIDENT 0; null when the opening lists no requirements. */
    static Integer fitPercent(List<ResumeInsight.Requirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return null;
        }
        int score = 0;
        for (ResumeInsight.Requirement r : requirements) {
            String a = r.assessment() == null ? "" : r.assessment().strip().toUpperCase(Locale.ROOT);
            score += a.equals("MET") ? 2 : a.equals("PARTIAL") ? 1 : 0;
        }
        return Math.round(score * 100f / (requirements.size() * 2));
    }

    static ResumeAnalyzer.Job jobOf(JobOpening job) {
        return new ResumeAnalyzer.Job(job.getTitle(), job.getDescription());
    }

    static String jobHash(JobOpening job) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (job.getTitle() + "\n" + (job.getDescription() == null ? "" : job.getDescription()))
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String nameFromFile(String fileName) {
        String stem = fileName.replaceAll("\\.[A-Za-z0-9]+$", "")
                .replaceAll("(?i)[_\\-.]*(resume|résumé|cv|curriculum vitae)[_\\-.]*", " ")
                .replaceAll("[_\\-.]+", " ").replaceAll("\\d+", " ").replaceAll("\\s+", " ").strip();
        return stem.isEmpty() ? "Unnamed candidate" : stem;
    }

    private static String clean(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
