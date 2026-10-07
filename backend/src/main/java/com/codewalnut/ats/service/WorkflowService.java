package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.ClientShare;
import com.codewalnut.ats.domain.DocumentRequest;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewFeedback;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.WorkflowDtos.ClientStatus;
import com.codewalnut.ats.dto.WorkflowDtos.Contact;
import com.codewalnut.ats.dto.WorkflowDtos.InterviewStatus;
import com.codewalnut.ats.dto.WorkflowDtos.NextStep;
import com.codewalnut.ats.dto.WorkflowDtos.Opening;
import com.codewalnut.ats.dto.WorkflowDtos.TestStatus;
import com.codewalnut.ats.dto.WorkflowDtos.Timeline;
import com.codewalnut.ats.dto.WorkflowDtos.TimelineItem;
import com.codewalnut.ats.dto.WorkflowDtos.WorkflowBoard;
import com.codewalnut.ats.dto.WorkflowDtos.WorkflowRow;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.ClientShareRepository;
import com.codewalnut.ats.repository.DocumentRequestRepository;
import com.codewalnut.ats.repository.InterviewFeedbackRepository;
import com.codewalnut.ats.repository.InterviewRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.repository.MessageRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The workflow view (WF-01…WF-06, ADR-0022): for every candidate, who contacted them and how, what
 * happened (tests, interviews, client, documents), whether they're waiting on us, and a suggested
 * next step. Built from what the app already records; calls and messages outside the app can be
 * logged. Suggestions are simple rules, never AI, and never change anything.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    static final int MAX_ROWS = 2000;
    static final Duration QUIET = Duration.ofDays(7);

    private static final Set<Stage> CLOSED = EnumSet.of(Stage.REJECTED, Stage.WITHDRAWN, Stage.JOINED);
    private static final Set<Stage> EARLY = EnumSet.of(Stage.SOURCED);

    /** The filters the page offers, in order. */
    static final Map<String, Predicate<WorkflowRow>> FILTERS = new LinkedHashMap<>();

    static {
        FILTERS.put("active", r -> !r.closed());
        FILTERS.put("urgent", r -> !r.closed() && r.nextStep() != null && r.nextStep().urgent());
        FILTERS.put("reply", WorkflowRow::awaitingReply);
        // Past screening someone has clearly been in touch, even if it isn't recorded here.
        FILTERS.put("never", r -> !r.closed() && r.contacts() == 0 && EARLY.contains(Stage.valueOf(r.stage())));
        FILTERS.put("quiet", r -> !r.closed() && r.lastContact() != null
                && r.lastContact().at().isBefore(Instant.now().minus(QUIET)));
        FILTERS.put("closed", WorkflowRow::closed);
    }


    /** Ways a recruiter can log contact made outside the app. */
    static final Map<String, String> HOW = Map.of(
            "CALL", "Call",
            "WHATSAPP", "WhatsApp (outside the app)",
            "EMAIL", "Email (outside the app)",
            "MEETING", "Met in person",
            "OTHER", "Other contact");

    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final MessageRepository messageRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository feedbackRepository;
    private final ClientShareRepository shareRepository;
    private final DocumentRequestRepository documentRequestRepository;
    private final JobOpeningRepository jobRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    // ---- the board ----

    @Transactional(readOnly = true)
    public WorkflowBoard board(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        // Over the cap, the most recently active people are kept.
        List<Application> applications = jobId != null
                ? applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId).stream().limit(MAX_ROWS).toList()
                : applicationRepository.findByJobStatusNotOrderByUpdatedAtDesc(JobStatus.CLOSED,
                        org.springframework.data.domain.PageRequest.of(0, MAX_ROWS));
        List<WorkflowRow> rows = rows(applications, Instant.now());
        Map<String, Long> counts = new LinkedHashMap<>();
        FILTERS.forEach((key, filter) -> counts.put(key, rows.stream().filter(filter).count()));
        List<Opening> openings = jobRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(j -> new Opening(j.getId(), j.getTitle(), j.getClient() == null ? null : j.getClient().getName(), j.getStatus().name()))
                .toList();
        return new WorkflowBoard(rows, counts, openings, accessPolicy.has(actor, Capability.MESSAGE_CANDIDATES));
    }

    List<WorkflowRow> rows(List<Application> applications, Instant now) {
        if (applications.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = applications.stream().map(Application::getId).toList();
        Map<UUID, List<ApplicationEvent>> events = eventRepository.findByApplicationIdIn(ids).stream()
                .collect(Collectors.groupingBy(e -> e.getApplication().getId()));
        Map<UUID, List<Message>> messages = messageRepository.findByApplicationIdInAndChannel(ids, MessageChannel.CANDIDATE).stream()
                .collect(Collectors.groupingBy(m -> m.getApplication().getId()));
        Map<UUID, List<AssessmentInvite>> invites = inviteRepository.findByApplicationIdInOrderBySentAtDesc(ids).stream()
                .filter(i -> i.getStatus() != AssessmentInvite.Status.CANCELLED)
                .collect(Collectors.groupingBy(i -> i.getApplication().getId()));
        List<Interview> allInterviews = interviewRepository.findByApplicationIdIn(ids);
        Map<UUID, List<Interview>> interviews = allInterviews.stream().collect(Collectors.groupingBy(i -> i.getApplication().getId()));
        Map<UUID, Long> submitted = allInterviews.isEmpty() ? Map.of()
                : feedbackRepository.findByInterviewIdIn(allInterviews.stream().map(Interview::getId).toList()).stream()
                        .filter(f -> !f.isDraft())
                        .collect(Collectors.groupingBy(InterviewFeedback::getInterviewId, Collectors.counting()));
        Map<UUID, ClientShare> shares = shareRepository.findByApplicationIdIn(ids).stream()
                .filter(s -> s.getRevokedAt() == null)
                .collect(Collectors.toMap(s -> s.getApplication().getId(), s -> s, (a, b) -> a));
        Map<UUID, List<DocumentRequest>> pendingDocs = documentRequestRepository
                .findByCandidateIdIn(applications.stream().map(a -> a.getCandidate().getId()).distinct().toList()).stream()
                .filter(d -> d.getFulfilledAt() == null)
                .collect(Collectors.groupingBy(DocumentRequest::getCandidateId));

        List<WorkflowRow> rows = new ArrayList<>();
        for (Application a : applications) {
            rows.add(row(a, events.getOrDefault(a.getId(), List.of()), messages.getOrDefault(a.getId(), List.of()),
                    invites.getOrDefault(a.getId(), List.of()), interviews.getOrDefault(a.getId(), List.of()), submitted,
                    shares.get(a.getId()), pendingDocs.getOrDefault(a.getCandidate().getId(), List.of()), now));
        }
        // Most pressing first: someone waiting on us, then urgent steps, then longest since contact.
        rows.sort(Comparator.comparing((WorkflowRow r) -> r.closed())
                .thenComparing(r -> !r.awaitingReply())
                .thenComparing(r -> r.nextStep() == null || !r.nextStep().urgent())
                .thenComparing(r -> r.lastContact() == null ? r.addedAt() : r.lastContact().at(),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        return rows;
    }

    private WorkflowRow row(Application a, List<ApplicationEvent> events, List<Message> messages, List<AssessmentInvite> invites,
            List<Interview> interviews, Map<UUID, Long> submitted, ClientShare share, List<DocumentRequest> pendingDocs, Instant now) {
        // Contacts: every way we reached the candidate.
        List<Contact> contacts = new ArrayList<>();
        Message lastCandidateMessage = null;
        Message lastStaffMessage = null;
        for (Message m : messages) {
            if (m.getAuthorType() == MessageAuthorType.STAFF) {
                contacts.add(new Contact(m.getCreatedAt(), how(m), m.getAuthorEmail()));
                lastStaffMessage = later(lastStaffMessage, m);
            } else if (m.getAuthorType() == MessageAuthorType.CANDIDATE) {
                lastCandidateMessage = later(lastCandidateMessage, m);
            }
        }
        for (ApplicationEvent e : events) {
            String how = switch (e.getType()) {
                case TEST_SENT -> "Test sent";
                case INTERVIEW_SCHEDULED -> "Interview invite";
                case INTERVIEW_MOVED -> "Interview rescheduled";
                case DOCS_REQUESTED -> "Documents requested";
                case CONTACT_LOGGED -> loggedHow(e.getNote());
                default -> null;
            };
            if (how != null) {
                contacts.add(new Contact(e.getCreatedAt(), how, e.getActorEmail()));
            }
        }
        invites.stream().filter(i -> i.getLastRemindedAt() != null)
                .forEach(i -> contacts.add(new Contact(i.getLastRemindedAt(), "Test reminder", i.getSentBy())));
        Contact last = contacts.stream().max(Comparator.comparing(Contact::at)).orElse(null);
        boolean awaitingReply = lastCandidateMessage != null
                && (lastStaffMessage == null || lastCandidateMessage.getCreatedAt().isAfter(lastStaffMessage.getCreatedAt()));

        Stage stage = a.getStage();
        Instant inStageSince = events.stream()
                .filter(e -> (e.getType() == ApplicationEventType.STAGE_CHANGED || e.getType() == ApplicationEventType.CREATED)
                        && e.getToStage() == stage)
                .map(ApplicationEvent::getCreatedAt)
                .max(Comparator.naturalOrder())
                .orElse(a.getCreatedAt());

        AssessmentInvite invite = invites.stream().max(Comparator.comparing(AssessmentInvite::getSentAt)).orElse(null);
        TestStatus test = invite == null ? null : new TestStatus(invite.getAssessment().getTitle(), testStatus(invite),
                invite.getPercent(), passed(invite), invite.getSubmittedAt() != null ? invite.getSubmittedAt() : invite.getSentAt());

        Interview interview = pickInterview(interviews, now);
        InterviewStatus interviewStatus = interview == null ? null : new InterviewStatus(interview.getId(),
                interview.getStatus() == com.codewalnut.ats.domain.InterviewStatus.CANCELLED ? "CANCELLED"
                        : interview.getStartAt().isAfter(now) ? "UPCOMING" : "DONE",
                interview.getStartAt(), submitted.getOrDefault(interview.getId(), 0L).intValue(),
                InterviewFeedbackService.panel(interview).size());

        JobOpening job = a.getJob();
        ClientStatus client = share == null ? null
                : new ClientStatus(share.getClient().getName(), share.getSharedAt(), share.getLastViewedAt());

        Instant lastActivity = java.util.stream.Stream.concat(events.stream().map(ApplicationEvent::getCreatedAt),
                        messages.stream().map(Message::getCreatedAt))
                .max(Comparator.naturalOrder()).orElse(a.getCreatedAt());

        boolean closed = CLOSED.contains(stage);
        NextStep next = closed ? null : nextStep(stage, job.getClient() != null, last, awaitingReply, invite, interviewStatus,
                interview, client, pendingDocs, inStageSince, a.getCreatedAt(), now);

        return new WorkflowRow(a.getId(), job.getId(), job.getTitle(), job.getClient() == null ? null : job.getClient().getName(),
                a.getCandidate().getId(), a.getCandidate().getName(), StringUtils.hasText(a.getCandidate().getPhone()), stage.name(), stage.getLabel(), closed, inStageSince,
                a.getCreatedAt(), last, contacts.size(), awaitingReply,
                lastCandidateMessage == null ? null : lastCandidateMessage.getCreatedAt(), test, interviewStatus, client,
                pendingDocs.size(), next, lastActivity);
    }

    /** The suggested next step: the first rule that applies. */
    static NextStep nextStep(Stage stage, boolean clientOpening, Contact last, boolean awaitingReply, AssessmentInvite invite,
            InterviewStatus interview, Interview interviewRow, ClientStatus client, List<DocumentRequest> pendingDocs,
            Instant inStageSince, Instant addedAt, Instant now) {
        if (awaitingReply) {
            return new NextStep("REPLY", "Reply to their message", true);
        }
        if (interview != null && "UPCOMING".equals(interview.status())) {
            return new NextStep("INTERVIEW_SOON", "Interview coming up", false);
        }
        if (interview != null && "DONE".equals(interview.status()) && interview.feedbackGiven() < Math.max(1, interview.panel())
                && stage.ordinal() <= Stage.INTERVIEWED.ordinal()) {
            boolean late = interviewRow.getEndAt().isBefore(now.minus(Duration.ofDays(1)));
            return new NextStep("FEEDBACK", "Collect interview feedback", late);
        }
        if (invite != null && invite.getStatus() == AssessmentInvite.Status.SUBMITTED && invite.getReviewedAt() == null) {
            return new NextStep("REVIEW_TEST", "Review the test result", true);
        }
        if (invite != null && invite.getStatus() == AssessmentInvite.Status.SENT && stage.ordinal() <= Stage.INTERVIEWED.ordinal()) {
            boolean overdue = (invite.getDueAt() != null && invite.getDueAt().isBefore(now))
                    || invite.getSentAt().isBefore(now.minus(Duration.ofDays(2)));
            return overdue ? new NextStep("REMIND_TEST", "Remind them about the test", true)
                    : new NextStep("WAIT_TEST", "Waiting for them to take the test", false);
        }
        if (invite != null && invite.getStatus() == AssessmentInvite.Status.STARTED) {
            return new NextStep("WAIT_TEST", "Waiting for them to finish the test", false);
        }
        if (!pendingDocs.isEmpty()) {
            boolean old = pendingDocs.stream().anyMatch(d -> d.getRequestedAt().isBefore(now.minus(Duration.ofDays(3))));
            return new NextStep("CHASE_DOCS", "Chase the requested documents", old);
        }
        Duration inStage = Duration.between(inStageSince, now);
        boolean stale = inStage.compareTo(Duration.ofDays(3)) > 0;
        return switch (stage) {
            case SOURCED -> {
                if (last == null) {
                    yield new NextStep("FIRST_CONTACT", "Get in touch: email, WhatsApp or call",
                            addedAt.isBefore(now.minus(Duration.ofDays(2))));
                }
                boolean passed = invite != null && Boolean.TRUE.equals(passed(invite));
                boolean failed = invite != null && invite.getStatus() == AssessmentInvite.Status.SUBMITTED
                        && Boolean.FALSE.equals(passed(invite));
                if (failed) {
                    yield new NextStep("DECIDE", "Test not passed: decide whether to continue", false);
                }
                if (interview == null && (passed || invite == null)) {
                    yield new NextStep(passed ? "SCHEDULE" : "TEST_OR_INTERVIEW",
                            passed ? "Passed the test: schedule an interview" : "Send a test or schedule an interview", stale);
                }
                yield new NextStep("DECIDE", "Decide the next stage", stale);
            }
            case INTERVIEWED -> new NextStep("DECIDE", "Decide: shortlist or reject", stale);
            case SHORTLISTED -> clientOpening && client == null
                    ? new NextStep("SHARE", "Share with the client", stale)
                    : clientOpening
                            ? new NextStep("CLIENT_FOLLOW_UP", "Get the client's feedback or decision", stale)
                            : new NextStep("DECIDE", "Decide: send an offer or reject", stale);
            case OFFER_SENT -> new NextStep("OFFER_FOLLOW_UP", "Follow up on the offer and confirm the joining date", stale);
            case ON_HOLD -> new NextStep("ON_HOLD", "On hold: check back with them",
                    last == null || last.at().isBefore(now.minus(Duration.ofDays(14))));
            default -> null;
        };
    }

    // ---- one candidate's timeline ----

    @Transactional(readOnly = true)
    public Timeline timeline(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        Application a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Candidate entry not found"));
        List<TimelineItem> items = new ArrayList<>();
        for (ApplicationEvent e : eventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId)) {
            TimelineItem item = item(e);
            if (item != null) {
                items.add(item);
            }
        }
        for (Message m : messageRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId)) {
            items.add(item(m));
        }
        items.sort(Comparator.comparing(TimelineItem::at).reversed());
        return new Timeline(applicationId, a.getCandidate().getName(), a.getJob().getTitle(), items);
    }

    /** Records a call, WhatsApp or meeting that happened outside the app. */
    @Transactional
    public Timeline logContact(AppUser actor, UUID applicationId, String how, String note) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        Application a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Candidate entry not found"));
        String label = HOW.get(how == null ? "" : how.toUpperCase(Locale.ROOT));
        if (label == null) {
            throw new IllegalArgumentException("how: choose call, WhatsApp, email, meeting or other");
        }
        String text = StringUtils.hasText(note) ? label + ": " + note.strip() : label;
        eventRepository.save(ApplicationEvent.builder()
                .application(a)
                .type(ApplicationEventType.CONTACT_LOGGED)
                .note(text)
                .actorEmail(actor.getEmail())
                .build());
        a.setUpdatedAt(Instant.now());
        auditService.record(actor, AuditAction.CONTACT_LOGGED, "Application", applicationId, Map.of("how", how));
        return timeline(actor, applicationId);
    }

    // ---- helpers ----

    private static TimelineItem item(ApplicationEvent e) {
        String note = e.getNote();
        String by = e.getActorEmail();
        return switch (e.getType()) {
            case CREATED -> new TimelineItem(e.getCreatedAt(), "ADDED",
                    "Added" + (e.getToStage() == null ? "" : " at " + e.getToStage().getLabel()), note, by);
            case STAGE_CHANGED -> new TimelineItem(e.getCreatedAt(), "STAGE",
                    (e.getFromStage() == null ? "" : e.getFromStage().getLabel() + " → ")
                            + (e.getToStage() == null ? "" : e.getToStage().getLabel()), note, by);
            case NOTE -> new TimelineItem(e.getCreatedAt(), "NOTE", "Note", note, by);
            case INTERVIEW_SCHEDULED -> new TimelineItem(e.getCreatedAt(), "INTERVIEW", "Interview scheduled", note, by);
            case INTERVIEW_MOVED -> new TimelineItem(e.getCreatedAt(), "INTERVIEW", "Interview rescheduled", note, by);
            case INTERVIEW_CANCELLED -> new TimelineItem(e.getCreatedAt(), "INTERVIEW", "Interview cancelled", note, by);
            case TEST_SENT -> new TimelineItem(e.getCreatedAt(), "TEST", "Test sent", note, by);
            case TEST_SUBMITTED -> new TimelineItem(e.getCreatedAt(), "TEST", "Test submitted", note, by);
            case SHARED_WITH_CLIENT -> new TimelineItem(e.getCreatedAt(), "CLIENT", "Shared with the client", note, by);
            case DOCS_REQUESTED -> new TimelineItem(e.getCreatedAt(), "DOCUMENT", "Documents requested", note, by);
            case DOC_UPLOADED -> new TimelineItem(e.getCreatedAt(), "DOCUMENT", "Document uploaded", note, by);
            case CONTACT_LOGGED -> new TimelineItem(e.getCreatedAt(), "CONTACT", loggedHow(note) + " (logged)",
                    note != null && note.contains(": ") ? note.substring(note.indexOf(": ") + 2) : null, by);
            // Emails and WhatsApps appear as their messages.
            case EMAIL_SENT, WHATSAPP_SENT -> null;
        };
    }

    private static TimelineItem item(Message m) {
        String body = preview(m.getBody());
        return switch (m.getChannel()) {
            case CANDIDATE -> m.getAuthorType() == MessageAuthorType.STAFF
                    ? new TimelineItem(m.getCreatedAt(), "CONTACT", how(m),
                            m.getSubject() == null ? body : m.getSubject() + " — " + body, m.getAuthorEmail())
                    : new TimelineItem(m.getCreatedAt(), "MESSAGE_IN", "Candidate wrote", body,
                            Objects.requireNonNullElse(m.getAuthorName(), m.getAuthorEmail()));
            case CLIENT -> m.getAuthorType() == MessageAuthorType.CLIENT
                    ? new TimelineItem(m.getCreatedAt(), "CLIENT", "Client wrote", body,
                            Objects.requireNonNullElse(m.getAuthorName(), m.getAuthorEmail()))
                    : new TimelineItem(m.getCreatedAt(), "CLIENT", "Message to the client", body, m.getAuthorEmail());
            case TEAM -> new TimelineItem(m.getCreatedAt(), "TEAM", "Team chat", body, m.getAuthorEmail());
        };
    }

    static String how(Message m) {
        boolean whatsapp = m.getWhatsappStatus() != null;
        if (m.isEmailed() && whatsapp) {
            return "Email + WhatsApp";
        }
        if (m.isEmailed()) {
            return "Email";
        }
        if (whatsapp) {
            return "WhatsApp";
        }
        return "Message on their candidate page";
    }

    private static String loggedHow(String note) {
        if (note == null) {
            return "Contact";
        }
        int colon = note.indexOf(": ");
        return colon > 0 ? note.substring(0, colon) : note;
    }

    /** Null until it's scored. */
    static Boolean passed(AssessmentInvite i) {
        return i.getStatus() != AssessmentInvite.Status.SUBMITTED || i.getPercent() == null ? null
                : i.getPercent() >= i.getAssessment().getPassPercent();
    }

    private static String testStatus(AssessmentInvite i) {
        return switch (i.getStatus()) {
            case SENT -> "Not started";
            case STARTED -> "In progress";
            case SUBMITTED -> i.getReviewedAt() == null ? "Submitted, not reviewed" : "Submitted";
            case EXPIRED -> "Expired";
            case CANCELLED -> "Cancelled";
        };
    }

    /** The next upcoming interview, else the latest held one, else the latest cancelled one. */
    private static Interview pickInterview(List<Interview> interviews, Instant now) {
        List<Interview> live = interviews.stream()
                .filter(i -> i.getStatus() != com.codewalnut.ats.domain.InterviewStatus.CANCELLED).toList();
        return live.stream().filter(i -> i.getStartAt().isAfter(now)).min(Comparator.comparing(Interview::getStartAt))
                .or(() -> live.stream().max(Comparator.comparing(Interview::getStartAt)))
                .or(() -> interviews.stream().max(Comparator.comparing(Interview::getStartAt)))
                .orElse(null);
    }

    private static Message later(Message current, Message m) {
        return current == null || m.getCreatedAt().isAfter(current.getCreatedAt()) ? m : current;
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        String b = body.strip().replaceAll("\\s+", " ");
        return b.length() <= 300 ? b : b.substring(0, 297) + "…";
    }
}
