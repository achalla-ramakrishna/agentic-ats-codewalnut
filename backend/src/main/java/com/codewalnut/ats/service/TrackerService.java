package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.Client;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.HiringType;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.TrackerDtos.AddCandidateRequest;
import com.codewalnut.ats.dto.TrackerDtos.ApplicationResponse;
import com.codewalnut.ats.dto.TrackerDtos.CreateClientRequest;
import com.codewalnut.ats.dto.TrackerDtos.CreateJobRequest;
import com.codewalnut.ats.dto.TrackerDtos.DashboardResponse;
import com.codewalnut.ats.dto.TrackerDtos.EventResponse;
import com.codewalnut.ats.dto.TrackerDtos.ImportOutcome;
import com.codewalnut.ats.dto.TrackerDtos.ImportRequest;
import com.codewalnut.ats.dto.TrackerDtos.ImportResult;
import com.codewalnut.ats.dto.TrackerDtos.ImportRow;
import com.codewalnut.ats.dto.TrackerDtos.JobResponse;
import com.codewalnut.ats.dto.TrackerDtos.MoveStageRequest;
import com.codewalnut.ats.dto.TrackerDtos.UpdateJobRequest;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.ClientRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The first, deliberately simple hiring workflow: clients, openings, candidates moving through
 * stages, notes, and an activity feed. See docs/features/hiring-tracker.md.
 */
@Service
@RequiredArgsConstructor
public class TrackerService {

    private final ClientRepository clientRepository;
    private final JobOpeningRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final CandidateDocumentRepository documentRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    // ---- clients ----

    @Transactional(readOnly = true)
    public List<Client> listClients(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_JOBS);
        return clientRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Client createClient(AppUser actor, CreateClientRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        String name = request.name().trim();
        if (clientRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A client with this name already exists");
        }
        Client client = clientRepository.save(Client.builder().name(name).notes(request.notes()).build());
        auditService.record(actor, AuditAction.CLIENT_CREATED, "Client", client.getId(), Map.of("name", name));
        return client;
    }

    // ---- openings ----

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_JOBS);
        Map<UUID, Map<Stage, Long>> counts = stageCounts();
        return jobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(j -> JobResponse.from(j, counts.getOrDefault(j.getId(), emptyCounts())))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.VIEW_JOBS);
        JobOpening job = job(id);
        return JobResponse.from(job, stageCounts().getOrDefault(id, emptyCounts()));
    }

    @Transactional
    public JobResponse createJob(AppUser actor, CreateJobRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Client client = null;
        if (request.clientId() != null) {
            client = clientRepository.findById(request.clientId())
                    .orElseThrow(() -> new NotFoundException("Client not found"));
        }
        if (request.hiringType() != HiringType.INTERNAL && client == null) {
            throw new IllegalArgumentException("clientId: a client opening needs a client");
        }
        JobOpening job = jobRepository.save(JobOpening.builder()
                .title(request.title().trim())
                .client(client)
                .hiringType(request.hiringType())
                .openings(request.openings())
                .description(request.description())
                .status(JobStatus.OPEN)
                .createdBy(actor.getEmail())
                .build());
        auditService.record(actor, AuditAction.JOB_CREATED, "JobOpening", job.getId(),
                Map.of("title", job.getTitle(), "hiringType", job.getHiringType()));
        return JobResponse.from(job, emptyCounts());
    }

    @Transactional
    public JobResponse updateJob(AppUser actor, UUID id, UpdateJobRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = job(id);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (request.status() != null && request.status() != job.getStatus()) {
            changes.put("status", Map.of("from", job.getStatus(), "to", request.status()));
            job.setStatus(request.status());
        }
        if (request.openings() != null && !request.openings().equals(job.getOpenings())) {
            changes.put("openings", request.openings());
            job.setOpenings(request.openings());
        }
        if (request.description() != null) {
            job.setDescription(request.description());
            changes.put("description", "changed");
        }
        if (!changes.isEmpty()) {
            auditService.record(actor, AuditAction.JOB_UPDATED, "JobOpening", id, changes);
        }
        return JobResponse.from(job, stageCounts().getOrDefault(id, emptyCounts()));
    }

    // ---- pipeline ----

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listApplications(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        job(jobId);
        List<Application> applications = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId);
        Map<UUID, Set<DocumentKind>> documents = documentKinds(applications);
        return applications.stream()
                .map(a -> ApplicationResponse.from(a, lastNote(a.getId()),
                        documents.getOrDefault(a.getCandidate().getId(), Set.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> searchApplications(AppUser actor, String q, Stage stage) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        String query = StringUtils.hasText(q) ? q.trim() : null;
        List<Application> applications = applicationRepository.search(query, stage).stream().limit(500).toList();
        Map<UUID, Set<DocumentKind>> documents = documentKinds(applications);
        return applications.stream()
                .map(a -> ApplicationResponse.from(a, null, documents.getOrDefault(a.getCandidate().getId(), Set.of())))
                .toList();
    }

    @Transactional
    public ApplicationResponse addCandidate(AppUser actor, UUID jobId, AddCandidateRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = openJob(jobId);
        String email = StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase(Locale.ROOT) : null;
        String phone = StringUtils.hasText(request.phone()) ? request.phone().replaceAll("[^0-9+]", "") : null;
        String name = request.name().trim().replaceAll("\\s+", " ");
        if (email == null && phone == null && applicationRepository.existsByJobIdAndCandidateNameIgnoreCase(jobId, name)) {
            throw new ConflictException(name + " is already in this opening");
        }
        Candidate candidate = findExisting(email, phone)
                .orElseGet(() -> candidateRepository.save(Candidate.builder()
                        .name(name).email(email).phone(phone).build()));
        if (applicationRepository.existsByJobIdAndCandidateId(jobId, candidate.getId())) {
            throw new ConflictException(candidate.getName() + " is already in this opening");
        }
        Application application = createApplication(actor, job, candidate, request.stage(), request.note());
        return ApplicationResponse.from(application, request.note());
    }

    /** Preview (dryRun) or import a pasted table. Only NEW and EXISTING_CANDIDATE rows are added. */
    @Transactional
    public ImportResult importCandidates(AppUser actor, UUID jobId, ImportRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = openJob(jobId);
        Set<String> seenInPaste = new HashSet<>();
        List<ImportRow> rows = new java.util.ArrayList<>();
        int added = 0;
        for (CandidateTableParser.Row row : CandidateTableParser.parse(request.text())) {
            List<String> issues = row.issues();
            if (!row.hasName()) {
                rows.add(new ImportRow(row.line(), row.name(), row.email(), row.phone(), ImportOutcome.ERROR, issues));
                continue;
            }
            String key = row.email() != null ? row.email() : row.phone() != null ? "tel:" + row.phone() : null;
            if (key != null && !seenInPaste.add(key)) {
                rows.add(new ImportRow(row.line(), row.name(), row.email(), row.phone(),
                        ImportOutcome.DUPLICATE_IN_PASTE, issues));
                continue;
            }
            Optional<Candidate> existing = findExisting(row.email(), row.phone());
            boolean alreadyIn = existing.isPresent()
                    ? applicationRepository.existsByJobIdAndCandidateId(jobId, existing.get().getId())
                    : key == null && applicationRepository.existsByJobIdAndCandidateNameIgnoreCase(jobId, row.name());
            if (alreadyIn) {
                rows.add(new ImportRow(row.line(), row.name(), row.email(), row.phone(),
                        ImportOutcome.ALREADY_IN_OPENING, issues));
                continue;
            }
            ImportOutcome outcome = existing.isPresent() ? ImportOutcome.EXISTING_CANDIDATE : ImportOutcome.NEW;
            if (!request.dryRun()) {
                Candidate candidate = existing.orElseGet(() -> candidateRepository.save(Candidate.builder()
                        .name(row.name()).email(row.email()).phone(row.phone()).build()));
                createApplication(actor, job, candidate, request.stage(), "Imported");
                added++;
            }
            rows.add(new ImportRow(row.line(), row.name(), row.email(), row.phone(), outcome, issues));
        }
        int importable = (int) rows.stream()
                .filter(r -> r.outcome() == ImportOutcome.NEW || r.outcome() == ImportOutcome.EXISTING_CANDIDATE)
                .count();
        if (!request.dryRun() && added > 0) {
            auditService.record(actor, AuditAction.CANDIDATES_IMPORTED, "JobOpening", jobId,
                    Map.of("added", added, "stage", request.stage()));
        }
        return new ImportResult(request.dryRun(), request.dryRun() ? importable : added,
                rows.size() - importable, rows);
    }

    @Transactional
    public ApplicationResponse moveStage(AppUser actor, UUID applicationId, MoveStageRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        Stage from = application.getStage();
        if (from == request.stage()) {
            return ApplicationResponse.from(application, lastNote(applicationId));
        }
        String note = StringUtils.hasText(request.note()) ? request.note().trim() : null;
        if (request.stage().requiresReason() && note == null) {
            throw new IllegalArgumentException("note: please give a reason when moving to " + request.stage().getLabel());
        }
        application.setStage(request.stage());
        applicationRepository.saveAndFlush(application);
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.STAGE_CHANGED)
                .fromStage(from)
                .toStage(request.stage())
                .note(note)
                .actorEmail(actor.getEmail())
                .build());
        return ApplicationResponse.from(application, note != null ? note : lastNote(applicationId));
    }

    @Transactional
    public EventResponse addNote(AppUser actor, UUID applicationId, String text) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        application.setUpdatedAt(java.time.Instant.now());
        return EventResponse.from(eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.NOTE)
                .note(text.trim())
                .actorEmail(actor.getEmail())
                .build()));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> history(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        application(applicationId);
        return eventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_DASHBOARD);
        if (!accessPolicy.has(actor, Capability.VIEW_JOBS)) {
            return new DashboardResponse(List.of(), List.of());
        }
        Map<UUID, Map<Stage, Long>> counts = stageCounts();
        List<JobResponse> open = jobRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(j -> j.getStatus() != JobStatus.CLOSED)
                .map(j -> JobResponse.from(j, counts.getOrDefault(j.getId(), emptyCounts())))
                .toList();
        List<EventResponse> recent = accessPolicy.has(actor, Capability.VIEW_CANDIDATES)
                ? eventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 25)).stream()
                        .map(EventResponse::from).toList()
                : List.of();
        return new DashboardResponse(open, recent);
    }

    // ---- helpers ----

    private Application createApplication(AppUser actor, JobOpening job, Candidate candidate, Stage stage, String note) {
        Application application = applicationRepository.save(Application.builder()
                .job(job).candidate(candidate).stage(stage).build());
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.CREATED)
                .toStage(stage)
                .note(StringUtils.hasText(note) ? note.trim() : null)
                .actorEmail(actor.getEmail())
                .build());
        return application;
    }

    private Optional<Candidate> findExisting(String email, String phone) {
        if (email != null) {
            Optional<Candidate> byEmail = candidateRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        if (phone != null) {
            return candidateRepository.findByPhone(phone).stream().findFirst();
        }
        return Optional.empty();
    }

    private String lastNote(UUID applicationId) {
        return eventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(e -> e.getNote())
                .filter(StringUtils::hasText)
                .filter(n -> !n.equals("Imported"))
                .findFirst()
                .orElse(null);
    }

    private Map<UUID, Set<DocumentKind>> documentKinds(List<Application> applications) {
        Map<UUID, Set<DocumentKind>> result = new HashMap<>();
        if (applications.isEmpty()) {
            return result;
        }
        List<UUID> ids = applications.stream().map(a -> a.getCandidate().getId()).distinct().toList();
        for (Object[] row : documentRepository.kindsFor(ids)) {
            result.computeIfAbsent((UUID) row[0], k -> java.util.EnumSet.noneOf(DocumentKind.class)).add((DocumentKind) row[1]);
        }
        return result;
    }

    private Map<UUID, Map<Stage, Long>> stageCounts() {
        Map<UUID, Map<Stage, Long>> result = new HashMap<>();
        for (Object[] row : applicationRepository.countByJobAndStage()) {
            result.computeIfAbsent((UUID) row[0], k -> emptyCounts()).put((Stage) row[1], (Long) row[2]);
        }
        return result;
    }

    private static Map<Stage, Long> emptyCounts() {
        return new EnumMap<>(Stage.class);
    }

    private JobOpening job(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new NotFoundException("Opening not found"));
    }

    private JobOpening openJob(UUID id) {
        JobOpening job = job(id);
        if (job.getStatus() == JobStatus.CLOSED) {
            throw new IllegalArgumentException("This opening is closed; reopen it to add candidates");
        }
        return job;
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Candidate entry not found"));
    }
}
