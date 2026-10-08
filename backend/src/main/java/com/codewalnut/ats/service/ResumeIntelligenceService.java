package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.ResumeAnalyzer;
import com.codewalnut.ats.client.ResumeInsight;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateInsight;
import com.codewalnut.ats.domain.InterviewFeedback;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.ResumeIntake;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AssessmentDtos.InviteView;
import com.codewalnut.ats.dto.InsightDtos.AnalyzeResult;
import com.codewalnut.ats.dto.InsightDtos.InsightDetail;
import com.codewalnut.ats.dto.InsightDtos.InsightSummary;
import com.codewalnut.ats.dto.InsightDtos.InsightsResponse;
import com.codewalnut.ats.dto.InsightDtos.IntakeItem;
import com.codewalnut.ats.dto.InsightDtos.IntakeProgress;
import com.codewalnut.ats.dto.InsightDtos.Suggestion;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CandidateInsightRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.repository.ResumeIntakeRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Résumé intelligence on an opening (ADR-0010): upload résumés in bulk, have the AI read each one
 * against the opening, and get an explained, advisory ordering of whom to contact next and who is
 * closest to selection. The AI never moves, rejects or contacts anyone; people do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeIntelligenceService {

    static final int MAX_FILES_PER_UPLOAD = 50;
    static final int SUGGESTIONS = 10;
    /** Below this, a match isn't strong enough to suggest contacting first. */
    static final int CONTACT_THRESHOLD = 50;
    private static final Set<Stage> EARLY = EnumSet.of(Stage.SOURCED, Stage.SCREENING);
    /** Interviewed or shortlisted: not yet offered. */
    private static final Set<Stage> ADVANCED = EnumSet.of(Stage.INTERVIEWED, Stage.SHORTLISTED);
    private static final Duration STUCK = Duration.ofMinutes(10);

    private final JobOpeningRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final ResumeIntakeRepository intakeRepository;
    private final CandidateInsightRepository insightRepository;
    private final CandidateDocumentRepository documentRepository;
    private final ResumeProcessor processor;
    private final ResumeAnalyzer analyzer;
    private final AiWorkQueue queue;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AssessmentInviteService tests;
    private final com.codewalnut.ats.repository.InterviewRepository interviewRepository;
    private final com.codewalnut.ats.repository.InterviewFeedbackRepository feedbackRepository;

    // ---- bulk upload ----

    @Transactional
    public IntakeProgress upload(AppUser actor, UUID jobId, List<MultipartFile> files) throws IOException {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Opening not found"));
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new IllegalArgumentException("This opening is closed; reopen it to add candidates");
        }
        requireAvailable();
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files: please choose at least one résumé");
        }
        if (files.size() > MAX_FILES_PER_UPLOAD) {
            throw new IllegalArgumentException("files: up to " + MAX_FILES_PER_UPLOAD + " résumés at a time");
        }
        Instant since = Instant.now().minusMillis(1);
        List<UUID> created = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = DocumentService.safeFileName(file.getOriginalFilename());
            byte[] data = file.getBytes();
            String extension = DocumentService.extension(name);
            String problem = data.length == 0 ? "The file is empty."
                    : data.length > DocumentService.MAX_BYTES ? "The file is larger than 10 MB."
                    : !Set.of("pdf", "docx", "doc").contains(extension) || !DocumentService.looksLike(extension, data)
                            ? "Only PDF or Word files can be read." : null;
            ResumeIntake intake = intakeRepository.save(ResumeIntake.builder()
                    .jobId(jobId)
                    .fileName(name)
                    .contentType(Objects.requireNonNullElse(DocumentService.contentType(extension), "application/octet-stream"))
                    .data(problem == null ? data : null)
                    .status(problem == null ? ResumeIntake.Status.PENDING : ResumeIntake.Status.FAILED)
                    .error(problem)
                    .processedAt(problem == null ? null : Instant.now())
                    .uploadedBy(actor.getEmail())
                    .build());
            if (problem == null) {
                created.add(intake.getId());
            }
        }
        auditService.record(actor, AuditAction.RESUMES_UPLOADED, "JobOpening", jobId,
                Map.of("files", files.size(), "accepted", created.size()));
        created.forEach(id -> queue.afterCommit(() -> processIntake(id)));
        return progressSince(jobId, since);
    }

    /** Progress of résumés uploaded to this opening in the last day. */
    @Transactional(readOnly = true)
    public IntakeProgress progress(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        job(jobId);
        return progressSince(jobId, Instant.now().minus(Duration.ofDays(1)));
    }

    private IntakeProgress progressSince(UUID jobId, Instant since) {
        List<ResumeIntakeRepository.Info> rows = intakeRepository.findByJobIdAndCreatedAtAfterOrderByCreatedAtAsc(jobId, since);
        Map<UUID, String> names = applicationRepository.findAllById(rows.stream()
                        .map(ResumeIntakeRepository.Info::getApplicationId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Application::getId, a -> a.getCandidate().getName()));
        List<IntakeItem> items = rows.stream()
                .map(r -> new IntakeItem(r.getId(), r.getFileName(), r.getStatus().name(),
                        r.getOutcome() != null ? r.getOutcome().name() : null, r.getApplicationId(),
                        r.getApplicationId() != null ? names.get(r.getApplicationId()) : null, r.getError()))
                .toList();
        return new IntakeProgress(items.size(),
                count(rows, r -> r.getStatus() == ResumeIntake.Status.PENDING),
                count(rows, r -> r.getStatus() == ResumeIntake.Status.DONE),
                count(rows, r -> r.getStatus() == ResumeIntake.Status.FAILED),
                count(rows, r -> r.getOutcome() == ResumeIntake.Outcome.NEW_CANDIDATE),
                count(rows, r -> r.getOutcome() == ResumeIntake.Outcome.EXISTING_CANDIDATE
                        || r.getOutcome() == ResumeIntake.Outcome.ALREADY_IN_OPENING),
                items);
    }

    void processIntake(UUID intakeId) {
        Optional<ResumeProcessor.Work> work = processor.startIntake(intakeId);
        if (work.isEmpty()) {
            return;
        }
        ResumeInsight insight;
        try {
            insight = analyzer.analyze(work.get().job(), work.get().file());
        } catch (CalendarException e) {
            processor.failIntake(intakeId, e.getMessage());
            return;
        }
        try {
            processor.finishIntake(intakeId, insight, analyzer.model(), work.get().jobHash());
        } catch (DataIntegrityViolationException first) {
            // Two résumés of the same new person read at once: the second now finds the first.
            try {
                processor.finishIntake(intakeId, insight, analyzer.model(), work.get().jobHash());
            } catch (RuntimeException e) {
                log.warn("Résumé intake {} failed", intakeId, e);
                processor.failIntake(intakeId, "Couldn't save this candidate. Please upload the résumé again.");
            }
        } catch (RuntimeException e) {
            log.warn("Résumé intake {} failed", intakeId, e);
            processor.failIntake(intakeId, e instanceof IllegalArgumentException
                    ? e.getMessage().replaceFirst("^file: ", "")
                    : "Couldn't save this candidate. Please upload the résumé again.");
        }
    }

    // ---- reading candidates already in the opening ----

    /** Reads the résumés of everyone in the opening who has no up-to-date reading. */
    @Transactional
    public AnalyzeResult analyzeAll(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = job(jobId);
        requireAvailable();
        String hash = ResumeProcessor.jobHash(job);
        List<Application> applications = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId);
        Map<UUID, CandidateInsight> insights = insightsFor(applications);
        int queued = 0;
        int noResume = 0;
        int upToDate = 0;
        for (Application application : applications) {
            Optional<CandidateDocumentRepository.Info> resume = processor.latestResume(application.getCandidate().getId());
            CandidateInsight insight = insights.get(application.getId());
            if (resume.isEmpty()) {
                noResume++;
                continue;
            }
            if (insight != null && insight.getStatus() == CandidateInsight.Status.DONE
                    && resume.get().getId().equals(insight.getDocumentId()) && hash.equals(insight.getJobHash())
                    && !outdated(insight)) {
                upToDate++;
                continue;
            }
            if (insight != null && insight.getStatus() == CandidateInsight.Status.PENDING
                    && insight.getUpdatedAt().isAfter(Instant.now().minus(STUCK))) {
                queued++;
                continue;
            }
            queue(application.getId());
            queued++;
        }
        auditService.record(actor, AuditAction.RESUMES_ANALYZED, "JobOpening", jobId,
                Map.of("queued", queued, "noResume", noResume));
        return new AnalyzeResult(queued, noResume, upToDate);
    }

    /** Reads one candidate's latest résumé again. */
    @Transactional
    public InsightDetail reanalyze(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        requireAvailable();
        if (processor.latestResume(application.getCandidate().getId()).isEmpty()) {
            throw new IllegalArgumentException("Upload this candidate's résumé first");
        }
        queue(applicationId);
        auditService.record(actor, AuditAction.RESUMES_ANALYZED, "Application", applicationId, Map.of("queued", 1));
        return new InsightDetail(applicationId, CandidateInsight.Status.PENDING.name(), null, null, null, false,
                null, null, null, null);
    }

    private void queue(UUID applicationId) {
        processor.markPending(applicationId);
        queue.afterCommit(() -> processInsight(applicationId));
    }

    void processInsight(UUID applicationId) {
        try {
            Optional<ResumeProcessor.Work> work = processor.startInsight(applicationId).map(processor::readInsight);
            if (work.isEmpty()) {
                return;
            }
            ResumeInsight insight = analyzer.analyze(work.get().job(), work.get().file());
            processor.finishInsight(applicationId, work.get().documentId(), insight, analyzer.model(), work.get().jobHash());
        } catch (CalendarException e) {
            processor.failInsight(applicationId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Reading résumé for {} failed", applicationId, e);
            processor.failInsight(applicationId, "Something went wrong reading this résumé. Try again.");
        }
    }

    /** A recruiter or the candidate added a new résumé: read it for their active applications. */
    @TransactionalEventListener
    public void onResumeAdded(ResumeAddedEvent event) {
        if (!analyzer.available()) {
            return;
        }
        for (Application application : applicationRepository.findByCandidateIdOrderByCreatedAtDesc(event.candidateId())) {
            if (!application.getStage().isExit() && application.getJob().getStatus() != JobStatus.CLOSED) {
                UUID id = application.getId();
                processor.markPending(id);
                queue.submit(() -> processInsight(id));
            }
        }
    }

    /** Picks up work a restart interrupted. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        if (!analyzer.available()) {
            return;
        }
        List<UUID> intakes = intakeRepository.findPendingIds();
        List<UUID> insights = insightRepository.findPendingApplicationIds();
        if (!intakes.isEmpty() || !insights.isEmpty()) {
            log.info("Resuming {} résumé upload(s) and {} reading(s)", intakes.size(), insights.size());
        }
        intakes.forEach(id -> queue.submit(() -> processIntake(id)));
        insights.forEach(id -> queue.submit(() -> processInsight(id)));
    }

    // ---- results ----

    @Transactional(readOnly = true)
    public InsightsResponse insights(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        JobOpening job = job(jobId);
        String hash = ResumeProcessor.jobHash(job);
        List<Application> applications = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId);
        Map<UUID, CandidateInsight> insights = insightsFor(applications);
        List<InsightSummary> summaries = new ArrayList<>();
        Map<UUID, ResumeInsight> profiles = new java.util.HashMap<>();
        for (CandidateInsight insight : insights.values()) {
            ResumeInsight profile = parse(insight);
            if (profile != null) {
                profiles.put(insight.getApplicationId(), profile);
            }
            summaries.add(summary(insight, profile, hash));
        }
        Map<UUID, InsightSummary> byApplication = summaries.stream()
                .collect(Collectors.toMap(InsightSummary::applicationId, Function.identity()));

        Map<UUID, InviteView> latestTest = new java.util.HashMap<>();
        for (InviteView t : tests.latestSubmitted(applications.stream().map(Application::getId).toList())) {
            latestTest.putIfAbsent(t.applicationId(), t);
        }
        Comparator<Application> byTest = Comparator.comparing(
                (Application a) -> latestTest.containsKey(a.getId()) ? latestTest.get(a.getId()).percent() : null,
                Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Application> byFit = Comparator.comparing(
                (Application a) -> fit(byApplication.get(a.getId())), Comparator.nullsLast(Comparator.reverseOrder()));
        List<Suggestion> contactNext = applications.stream()
                .filter(a -> EARLY.contains(a.getStage()))
                .filter(a -> Objects.requireNonNullElse(fit(byApplication.get(a.getId())), -1) >= CONTACT_THRESHOLD)
                .sorted(byFit)
                .limit(SUGGESTIONS)
                .map(a -> suggestion(a, byApplication.get(a.getId()), profiles.get(a.getId()), latestTest.get(a.getId()), false))
                .toList();
        Map<UUID, List<InterviewFeedback.Recommendation>> panel = recommendations(applications);
        Map<UUID, Integer> readiness = new java.util.HashMap<>();
        for (Application a : applications) {
            if (ADVANCED.contains(a.getStage())) {
                Integer r = readiness(a, panel.get(a.getId()), latestTest.get(a.getId()), fit(byApplication.get(a.getId())));
                if (r != null) {
                    readiness.put(a.getId(), r);
                }
            }
        }
        // Interviewed or shortlisted, the panel not against them, best overall evidence first (AI-32).
        List<Suggestion> closest = applications.stream()
                .filter(a -> ADVANCED.contains(a.getStage()))
                .filter(a -> !panelAgainst(panel.get(a.getId())))
                .sorted(Comparator.comparing((Application a) -> readiness.get(a.getId()), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparing((Application a) -> a.getStage().ordinal()).reversed()))
                .limit(SUGGESTIONS)
                .map(a -> withReadiness(suggestion(a, byApplication.get(a.getId()), profiles.get(a.getId()), latestTest.get(a.getId()),
                        true, panel.get(a.getId())), readiness.get(a.getId())))
                .toList();

        // Résumé counts by whether a résumé is attached at all, so "to read" never includes people without one.
        Set<UUID> withResume = new java.util.HashSet<>();
        for (Object[] row : documentRepository.kindsFor(applications.stream().map(a -> a.getCandidate().getId()).distinct().toList())) {
            if (row[1] == com.codewalnut.ats.domain.DocumentKind.ORIGINAL_RESUME) {
                withResume.add((UUID) row[0]);
            }
        }
        int noResume = (int) applications.stream().filter(a -> !withResume.contains(a.getCandidate().getId())).count();
        int notAnalyzed = (int) applications.stream()
                .filter(a -> withResume.contains(a.getCandidate().getId()))
                .filter(a -> !insights.containsKey(a.getId()) || insights.get(a.getId()).getStatus() == CandidateInsight.Status.NO_RESUME)
                .count();
        return new InsightsResponse(analyzer.available(), StringUtils.hasText(job.getDescription()),
                countStatus(insights, CandidateInsight.Status.DONE), countStatus(insights, CandidateInsight.Status.PENDING),
                countStatus(insights, CandidateInsight.Status.FAILED), noResume, notAnalyzed, summaries, contactNext, closest);
    }

    /** Submitted interview recommendations per application (drafts and no-shows left out). */
    private Map<UUID, List<InterviewFeedback.Recommendation>> recommendations(List<Application> applications) {
        List<com.codewalnut.ats.domain.Interview> interviews = applications.isEmpty() ? List.of()
                : interviewRepository.findByApplicationIdIn(applications.stream().map(Application::getId).toList()).stream()
                        .filter(i -> i.getStatus() != com.codewalnut.ats.domain.InterviewStatus.CANCELLED).toList();
        if (interviews.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> applicationOf = interviews.stream()
                .collect(Collectors.toMap(com.codewalnut.ats.domain.Interview::getId, i -> i.getApplication().getId()));
        Map<UUID, List<InterviewFeedback.Recommendation>> out = new java.util.HashMap<>();
        for (InterviewFeedback f : feedbackRepository.findByInterviewIdIn(applicationOf.keySet())) {
            if (!f.isDraft() && f.getRecommendation() != null) {
                out.computeIfAbsent(applicationOf.get(f.getInterviewId()), k -> new ArrayList<>()).add(f.getRecommendation());
            }
        }
        return out;
    }

    private static int points(InterviewFeedback.Recommendation r) {
        return switch (r) {
            case STRONG_YES -> 100;
            case YES -> 75;
            case NO -> 25;
            case STRONG_NO -> 0;
        };
    }

    static boolean panelAgainst(List<InterviewFeedback.Recommendation> recs) {
        return recs != null && !recs.isEmpty() && recs.stream().mapToInt(ResumeIntelligenceService::points).average().orElse(100) < 50;
    }

    /**
     * 0–100. Interview feedback is half (no feedback yet counts as a neutral 50, so good feedback lifts
     * someone above a résumé-only candidate); the other half is the test score and résumé match, over
     * whichever exist. Shortlisted adds 5. Null when there is no evidence at all.
     */
    static Integer readiness(Application a, List<InterviewFeedback.Recommendation> recs, InviteView test, Integer fit) {
        boolean hasFeedback = recs != null && !recs.isEmpty();
        boolean hasTest = test != null && test.percent() != null;
        if (!hasFeedback && !hasTest && fit == null) {
            return null;
        }
        double feedback = hasFeedback ? recs.stream().mapToInt(ResumeIntelligenceService::points).average().orElse(50) : 50;
        double rest = 0;
        double weight = 0;
        if (hasTest) {
            rest += 0.4 * test.percent();
            weight += 0.4;
        }
        if (fit != null) {
            rest += 0.6 * fit;
            weight += 0.6;
        }
        double other = weight == 0 ? 50 : rest / weight;
        int score = (int) Math.round(0.5 * feedback + 0.5 * other) + (a.getStage() == Stage.SHORTLISTED ? 5 : 0);
        return Math.min(100, score);
    }

    private static Suggestion withReadiness(Suggestion s, Integer readiness) {
        return new Suggestion(s.applicationId(), s.candidateName(), s.stageLabel(), s.fitPercent(), s.reason(), readiness);
    }

    private static String panelSummary(List<InterviewFeedback.Recommendation> recs) {
        Map<InterviewFeedback.Recommendation, Long> counts = recs.stream()
                .collect(Collectors.groupingBy(Function.identity(), () -> new java.util.EnumMap<>(InterviewFeedback.Recommendation.class),
                        Collectors.counting()));
        List<String> parts = new ArrayList<>();
        java.util.List<InterviewFeedback.Recommendation> order = List.of(InterviewFeedback.Recommendation.STRONG_YES,
                InterviewFeedback.Recommendation.YES, InterviewFeedback.Recommendation.NO, InterviewFeedback.Recommendation.STRONG_NO);
        for (InterviewFeedback.Recommendation r : order) {
            Long n = counts.get(r);
            if (n != null) {
                String label = switch (r) {
                    case STRONG_YES -> "strong hire";
                    case YES -> "hire";
                    case NO -> "no hire";
                    case STRONG_NO -> "strong no hire";
                };
                parts.add(n + " " + label);
            }
        }
        return "panel: " + String.join(", ", parts);
    }

    @Transactional(readOnly = true)
    public InsightDetail insight(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        Application application = application(applicationId);
        CandidateInsight insight = insightRepository.findByApplicationId(applicationId).orElse(null);
        if (insight == null) {
            return new InsightDetail(applicationId, "NONE", null, null, null, false, null, null, null, null);
        }
        String fileName = insight.getDocumentId() == null ? null
                : documentRepository.findByCandidateIdOrderByUploadedAtDesc(application.getCandidate().getId()).stream()
                        .filter(d -> d.getId().equals(insight.getDocumentId()))
                        .map(CandidateDocumentRepository.Info::getFileName).findFirst().orElse(null);
        return new InsightDetail(applicationId, insight.getStatus().name(), insight.getFitPercent(), insight.getHeadline(),
                insight.getError(), isStale(insight, ResumeProcessor.jobHash(application.getJob())), insight.getModel(),
                insight.getAnalyzedAt(), fileName, parse(insight));
    }

    /** Each candidate's stack (Java, Python, MERN) from their résumé reading, to send the matching test (ASMT-39). */
    @Transactional(readOnly = true)
    public List<com.codewalnut.ats.dto.InsightDtos.Background> backgrounds(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        job(jobId);
        List<Application> applications = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId);
        Map<UUID, ResumeInsight> read = profiles(applications);
        return applications.stream().map(a -> {
            ResumeInsight profile = read.get(a.getId());
            TechBackground.Result r = TechBackground.of(profile);
            return new com.codewalnut.ats.dto.InsightDtos.Background(a.getId(),
                    r.track() == null ? null : r.track().name(), r.evidence(), profile != null);
        }).toList();
    }

    /** Readings for the assistant's questions, by application id. Only DONE ones. */
    @Transactional(readOnly = true)
    public Map<UUID, ResumeInsight> profiles(List<Application> applications) {
        Map<UUID, ResumeInsight> result = new java.util.HashMap<>();
        for (CandidateInsight insight : insightsFor(applications).values()) {
            ResumeInsight profile = parse(insight);
            if (profile != null) {
                result.put(insight.getApplicationId(), profile);
            }
        }
        return result;
    }

    Map<UUID, Integer> fits(List<Application> applications) {
        return insightsFor(applications).values().stream()
                .filter(i -> i.getFitPercent() != null)
                .collect(Collectors.toMap(CandidateInsight::getApplicationId, CandidateInsight::getFitPercent));
    }

    // ---- helpers ----

    private Suggestion suggestion(Application a, InsightSummary summary, ResumeInsight profile, InviteView test,
            boolean advanced) {
        return suggestion(a, summary, profile, test, advanced, null);
    }

    private Suggestion suggestion(Application a, InsightSummary summary, ResumeInsight profile, InviteView test,
            boolean advanced, List<InterviewFeedback.Recommendation> panel) {
        List<String> parts = new ArrayList<>();
        if (advanced) {
            parts.add("At " + a.getStage().getLabel());
        }
        if (panel != null && !panel.isEmpty()) {
            parts.add(panelSummary(panel));
        } else if (advanced) {
            parts.add("no interview feedback yet");
        }
        if (summary != null && summary.total() > 0 && summary.fitPercent() != null) {
            parts.add("meets " + summary.met() + " of " + summary.total() + " requirements"
                    + (summary.partial() > 0 ? " (" + summary.partial() + " partly)" : ""));
        } else if (advanced && (summary == null || !"DONE".equals(summary.status()))) {
            parts.add("résumé not read yet");
        }
        if (test != null) {
            parts.add(test.title() + " test " + test.percent() + "%" + (Boolean.TRUE.equals(test.passed()) ? " (passed)" : ""));
        }
        if (profile != null && profile.strengths() != null && !profile.strengths().isEmpty()) {
            parts.add(profile.strengths().get(0));
        }
        String reason = String.join(" · ", parts);
        return new Suggestion(a.getId(), a.getCandidate().getName(), a.getStage().getLabel(),
                summary != null ? summary.fitPercent() : null,
                reason.isEmpty() ? reason : Character.toUpperCase(reason.charAt(0)) + reason.substring(1), null);
    }

    private InsightSummary summary(CandidateInsight insight, ResumeInsight profile, String hash) {
        List<ResumeInsight.Requirement> requirements = profile != null && profile.requirements() != null
                ? profile.requirements() : List.of();
        return new InsightSummary(insight.getApplicationId(), insight.getStatus().name(), insight.getFitPercent(),
                insight.getHeadline(), insight.getError(), isStale(insight, hash),
                (int) requirements.stream().filter(r -> "MET".equalsIgnoreCase(r.assessment())).count(),
                (int) requirements.stream().filter(r -> "PARTIAL".equalsIgnoreCase(r.assessment())).count(),
                requirements.size(),
                profile != null && profile.skills() != null ? profile.skills().stream().limit(20).toList() : List.of(),
                profile != null && profile.projects() != null ? profile.projects().size() : 0,
                profile != null && profile.experienceMonths() != null ? profile.experienceMonths() : 0,
                profile != null && profile.graduationYear() != null && profile.graduationYear() > 0
                        ? profile.graduationYear() : null);
    }

    /** The opening changed since, or the reading predates the profile fields (college, degree …). */
    private static boolean isStale(CandidateInsight insight, String hash) {
        return insight.getStatus() == CandidateInsight.Status.DONE
                && (insight.getJobHash() != null && !insight.getJobHash().equals(hash) || outdated(insight));
    }

    static boolean outdated(CandidateInsight insight) {
        return insight.getData() != null && !insight.getData().contains("\"college\"");
    }

    private static Integer fit(InsightSummary summary) {
        return summary == null || !"DONE".equals(summary.status()) ? null : summary.fitPercent();
    }

    private ResumeInsight parse(CandidateInsight insight) {
        if (insight.getStatus() != CandidateInsight.Status.DONE || insight.getData() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(insight.getData(), ResumeInsight.class);
        } catch (IOException e) {
            log.warn("Unreadable insight {}", insight.getId());
            return null;
        }
    }

    private Map<UUID, CandidateInsight> insightsFor(List<Application> applications) {
        if (applications.isEmpty()) {
            return Map.of();
        }
        return insightRepository.findByApplicationIdIn(applications.stream().map(Application::getId).toList()).stream()
                .collect(Collectors.toMap(CandidateInsight::getApplicationId, Function.identity()));
    }

    private static int countStatus(Map<UUID, CandidateInsight> insights, CandidateInsight.Status status) {
        return (int) insights.values().stream().filter(i -> i.getStatus() == status).count();
    }

    private static <T> int count(List<T> rows, java.util.function.Predicate<T> test) {
        return (int) rows.stream().filter(test).count();
    }

    private void requireAvailable() {
        if (!analyzer.available()) {
            throw new CalendarException("AI résumé reading isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
        }
    }

    private JobOpening job(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new NotFoundException("Opening not found"));
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Candidate entry not found"));
    }
}
