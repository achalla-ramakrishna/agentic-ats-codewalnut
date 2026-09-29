package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.Client;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.HiringType;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.domain.WorkMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Request/response shapes for the hiring tracker (clients, openings, pipeline, dashboard). */
public final class TrackerDtos {

    private TrackerDtos() {}

    public record StageOption(Stage key, String label, boolean exit) {}

    public record ClientResponse(UUID id, String name, String notes) {
        public static ClientResponse from(Client c) {
            return new ClientResponse(c.getId(), c.getName(), c.getNotes());
        }
    }

    public record CreateClientRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String notes) {}

    public record JobResponse(
            UUID id, String title, ClientResponse client, HiringType hiringType, String hiringTypeLabel,
            Integer openings, JobStatus status, String description, String location, WorkMode workMode,
            String employmentType, boolean published, String publicSlug, Instant createdAt,
            Map<Stage, Long> stageCounts, long total) {

        public static JobResponse from(JobOpening j, Map<Stage, Long> counts) {
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            return new JobResponse(j.getId(), j.getTitle(), j.getClient() == null ? null : ClientResponse.from(j.getClient()),
                    j.getHiringType(), j.getHiringType().getLabel(), j.getOpenings(), j.getStatus(),
                    j.getDescription(), j.getLocation(), j.getWorkMode(), j.getEmploymentType(), j.isPublished(),
                    j.getPublicSlug(), j.getCreatedAt(), counts, total);
        }
    }

    public record CreateJobRequest(
            @NotBlank @Size(max = 200) String title,
            UUID clientId,
            @NotNull HiringType hiringType,
            @Min(1) Integer openings,
            @Size(max = 20000) String description) {}

    /** Partial update: null fields are left unchanged. */
    public record UpdateJobRequest(
            @Size(min = 1, max = 200) String title,
            JobStatus status,
            @Min(1) Integer openings,
            @Size(max = 20000) String description,
            @Size(max = 200) String location,
            WorkMode workMode,
            @Size(max = 100) String employmentType,
            Boolean published) {}

    /** What anyone with the link sees. No client name, no counts, no internal data. */
    public record PublicJobResponse(
            String slug, String title, String company, String location, String workMode, String employmentType,
            String description, boolean acceptingApplications) {}

    /** A candidate's own view of an application: a simple status, never internal stages or notes. */
    public record MyApplicationResponse(String slug, String jobTitle, String status, Instant appliedAt) {}

    public record ApplicationResponse(
            UUID id, UUID jobId, String jobTitle, UUID candidateId, String name, String email, String phone,
            Stage stage, String stageLabel, Instant updatedAt, String lastNote, Set<DocumentKind> documents) {

        public static ApplicationResponse from(Application a, String lastNote) {
            return from(a, lastNote, Set.of());
        }

        public static ApplicationResponse from(Application a, String lastNote, Set<DocumentKind> documents) {
            return new ApplicationResponse(a.getId(), a.getJob().getId(), a.getJob().getTitle(),
                    a.getCandidate().getId(), a.getCandidate().getName(), a.getCandidate().getEmail(),
                    a.getCandidate().getPhone(), a.getStage(), a.getStage().getLabel(), a.getUpdatedAt(), lastNote,
                    documents);
        }
    }

    public record AddCandidateRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @NotNull Stage stage,
            @Size(max = 5000) String note) {}

    /** Contact details; a blank value clears that field (the name can't be cleared). */
    public record UpdateCandidateRequest(
            @Size(max = 200) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone) {}

    public record CandidateContactResponse(UUID id, String name, String email, String phone) {}

    public record ImportRequest(@NotBlank @Size(max = 200_000) String text, @NotNull Stage stage, boolean dryRun) {}

    public enum ImportOutcome { NEW, EXISTING_CANDIDATE, ALREADY_IN_OPENING, DUPLICATE_IN_PASTE, ERROR }

    public record ImportRow(
            int line, String name, String email, String phone, ImportOutcome outcome, List<String> issues) {}

    public record ImportResult(boolean dryRun, int added, int skipped, List<ImportRow> rows) {}

    public record MoveStageRequest(@NotNull Stage stage, @Size(max = 5000) String note) {}

    public record NoteRequest(@NotBlank @Size(max = 5000) String text) {}

    public record EventResponse(
            UUID id, UUID applicationId, String candidateName, String jobTitle, ApplicationEventType type,
            Stage fromStage, Stage toStage, String note, String actorEmail, Instant createdAt) {

        public static EventResponse from(ApplicationEvent e) {
            return new EventResponse(e.getId(), e.getApplication().getId(),
                    e.getApplication().getCandidate().getName(), e.getApplication().getJob().getTitle(),
                    e.getType(), e.getFromStage(), e.getToStage(), e.getNote(), e.getActorEmail(), e.getCreatedAt());
        }
    }

    public record DashboardResponse(List<JobResponse> openJobs, List<EventResponse> recentActivity) {}
}
