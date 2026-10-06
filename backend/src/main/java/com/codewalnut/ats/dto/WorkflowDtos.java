package com.codewalnut.ats.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The workflow view (WF-01…): who was contacted, what happened, what to do next. */
public final class WorkflowDtos {

    private WorkflowDtos() {}

    /** how: e.g. "Email", "WhatsApp", "Call", "Test sent", "Interview invite". */
    public record Contact(Instant at, String how, String by) {}

    public record TestStatus(String title, String status, Integer percent, Boolean passed, Instant at) {}

    /** status: UPCOMING, DONE or CANCELLED. */
    public record InterviewStatus(UUID interviewId, String status, Instant startAt, int feedbackGiven, int panel) {}

    public record ClientStatus(String client, Instant sharedAt, Instant viewedAt) {}

    /** code: a stable key; label: what to do; urgent: overdue or someone is waiting on us. */
    public record NextStep(String code, String label, boolean urgent) {}

    public record WorkflowRow(
            UUID applicationId, UUID jobId, String jobTitle, String clientName, UUID candidateId, String candidateName, boolean hasPhone,
            String stage, String stageLabel, boolean closed, Instant inStageSince, Instant addedAt,
            Contact lastContact, int contacts, boolean awaitingReply, Instant candidateWroteAt,
            TestStatus test, InterviewStatus interview, ClientStatus client, int documentsPending,
            NextStep nextStep, Instant lastActivityAt) {}

    public record Opening(UUID id, String title, String clientName, String status) {}

    /** counts: rows per filter (see WorkflowService.FILTERS). */
    public record WorkflowBoard(List<WorkflowRow> rows, Map<String, Long> counts, List<Opening> openings, boolean canLog) {}

    /** kind: CONTACT, MESSAGE_IN, STAGE, TEST, INTERVIEW, CLIENT, DOCUMENT, NOTE, TEAM, ADDED. */
    public record TimelineItem(Instant at, String kind, String title, String detail, String by) {}

    public record Timeline(UUID applicationId, String candidateName, String jobTitle, List<TimelineItem> items) {}

    /** how: CALL, WHATSAPP, EMAIL, MEETING, OTHER. */
    public record LogContactRequest(@NotNull String how, @Size(max = 2000) String note) {}
}
