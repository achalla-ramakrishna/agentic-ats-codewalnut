package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarClient;
import com.codewalnut.ats.client.CalendarClient.Invite;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewStatus;
import com.codewalnut.ats.dto.InterviewDtos.CandidateInterviewResponse;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import com.codewalnut.ats.dto.InterviewDtos.LogInterviewRequest;
import com.codewalnut.ats.dto.InterviewDtos.ScheduleInterviewRequest;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.InterviewRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Interviews on the organiser's Google Calendar with a Meet link; Google emails the invitation
 * to the candidate and the interviewers. See docs/features/interviews-and-scorecards.md (INT-12…).
 */
@Service
@RequiredArgsConstructor
public class InterviewService {

    static final Pattern EMAIL = Pattern.compile("^[^@\\s,;<>]+@[^@\\s,;<>]+\\.[^@\\s,;<>]+$");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a z", Locale.ENGLISH);

    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final ApplicationEventRepository eventRepository;
    private final CalendarClient calendarClient;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional
    public InterviewResponse schedule(AppUser actor, UUID applicationId, ScheduleInterviewRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        String candidateEmail = application.getCandidate().getEmail();
        if (!StringUtils.hasText(candidateEmail)) {
            throw new IllegalArgumentException(
                    "email: add the candidate's email address first. The invitation is sent there");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone().trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("timeZone: unknown time zone");
        }
        Instant start = request.startAt();
        if (start.isBefore(Instant.now().minus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("startAt: pick a time in the future");
        }
        Instant end = start.plus(Duration.ofMinutes(request.durationMinutes()));
        List<String> interviewers = interviewers(request.interviewerEmails(), candidateEmail);
        String title = StringUtils.hasText(request.title())
                ? request.title().trim()
                : "CodeWalnut interview – " + application.getJob().getTitle();
        String message = StringUtils.hasText(request.message()) ? request.message().trim() : null;

        List<String> attendees = new ArrayList<>();
        attendees.add(candidateEmail);
        attendees.addAll(interviewers);
        CalendarClient.Event event = calendarClient.create(
                new Invite(title, description(message), start, end, zone.getId(), attendees));

        Interview interview = interviewRepository.save(Interview.builder()
                .application(application)
                .title(title)
                .startAt(start)
                .endAt(end)
                .timeZone(zone.getId())
                .interviewerEmails(interviewers.isEmpty() ? null : String.join(",", interviewers))
                .message(message)
                .status(InterviewStatus.SCHEDULED)
                .meetLink(event.meetLink())
                .calendarEventId(event.id())
                .calendarLink(event.htmlLink())
                .organizerEmail(actor.getEmail())
                .build());
        application.setUpdatedAt(Instant.now());
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.INTERVIEW_SCHEDULED)
                .note(title + ", " + WHEN.format(start.atZone(zone)))
                .actorEmail(actor.getEmail())
                .build());
        auditService.record(actor, AuditAction.INTERVIEW_SCHEDULED, "Interview", interview.getId(),
                Map.of("applicationId", applicationId, "startAt", start.toString(), "interviewers", interviewers.size()));
        return InterviewResponse.from(interview);
    }

    /**
     * Records an interview that was held (or is happening) outside the app, e.g. a Meet someone set
     * up by hand, so it shows in the list and the panel can give feedback. No calendar event or email.
     */
    @Transactional
    public InterviewResponse log(AppUser actor, UUID applicationId, LogInterviewRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Application application = application(applicationId);
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone().trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("timeZone: unknown time zone");
        }
        Instant start = request.startAt();
        if (start.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("startAt: this is for interviews that already happened; schedule future ones instead");
        }
        if (start.isBefore(Instant.now().minus(Duration.ofDays(90)))) {
            throw new IllegalArgumentException("startAt: pick a date in the last 90 days");
        }
        List<String> interviewers = interviewers(request.interviewerEmails(), application.getCandidate().getEmail());
        String title = StringUtils.hasText(request.title())
                ? request.title().trim()
                : "CodeWalnut interview – " + application.getJob().getTitle();
        Interview interview = interviewRepository.save(Interview.builder()
                .application(application)
                .title(title)
                .startAt(start)
                .endAt(start.plus(Duration.ofMinutes(request.durationMinutes())))
                .timeZone(zone.getId())
                .interviewerEmails(interviewers.isEmpty() ? null : String.join(",", interviewers))
                .status(InterviewStatus.SCHEDULED)
                .organizerEmail(actor.getEmail())
                .build());
        application.setUpdatedAt(Instant.now());
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.INTERVIEW_SCHEDULED)
                .note("Logged (held outside the app): " + title + ", " + WHEN.format(start.atZone(zone)))
                .actorEmail(actor.getEmail())
                .build());
        auditService.record(actor, AuditAction.INTERVIEW_LOGGED, "Interview", interview.getId(),
                Map.of("applicationId", applicationId, "startAt", start.toString(), "interviewers", interviewers.size()));
        return InterviewResponse.from(interview);
    }

    @Transactional
    public InterviewResponse cancel(AppUser actor, UUID interviewId, String reason) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new NotFoundException("Interview not found"));
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            return InterviewResponse.from(interview);
        }
        if (!interview.getOrganizerEmail().equalsIgnoreCase(actor.getEmail())) {
            throw new ConflictException("This interview is on " + interview.getOrganizerEmail()
                    + "'s Google Calendar. Ask them to cancel it.");
        }
        if (interview.getCalendarEventId() != null) {
            calendarClient.cancel(interview.getCalendarEventId());
        }
        String why = StringUtils.hasText(reason) ? reason.trim() : null;
        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setCancelReason(why);
        interview.setCancelledAt(Instant.now());
        Application application = interview.getApplication();
        application.setUpdatedAt(Instant.now());
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.INTERVIEW_CANCELLED)
                .note(interview.getTitle() + (why != null ? ". Reason: " + why : ""))
                .actorEmail(actor.getEmail())
                .build());
        auditService.record(actor, AuditAction.INTERVIEW_CANCELLED, "Interview", interviewId,
                Map.of("applicationId", application.getId()));
        return InterviewResponse.from(interview);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> forApplication(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        application(applicationId);
        return interviewRepository.findByApplicationIdOrderByStartAtDesc(applicationId).stream()
                .map(InterviewResponse::from)
                .toList();
    }

    /** Upcoming interviews. People who can't see candidates (interviewers) see only their own panels. */
    @Transactional(readOnly = true)
    public List<InterviewResponse> upcoming(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        boolean all = accessPolicy.has(actor, Capability.VIEW_CANDIDATES);
        String me = actor.getEmail().toLowerCase(Locale.ROOT);
        return interviewRepository.findByStatusAndEndAtAfterOrderByStartAtAsc(InterviewStatus.SCHEDULED, Instant.now())
                .stream()
                .filter(i -> all || i.interviewers().contains(me) || i.getOrganizerEmail().equalsIgnoreCase(me))
                .map(InterviewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateInterviewResponse> forCandidateEmail(String email) {
        return candidateRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .map(c -> interviewRepository.findByApplicationCandidateIdAndStatusAndEndAtAfterOrderByStartAtAsc(
                                c.getId(), InterviewStatus.SCHEDULED, Instant.now()).stream()
                        .map(CandidateInterviewResponse::from)
                        .toList())
                .orElse(List.of());
    }

    static List<String> interviewers(List<String> raw, String candidateEmail) {
        Set<String> result = new LinkedHashSet<>();
        if (raw != null) {
            for (String entry : raw) {
                if (!StringUtils.hasText(entry)) {
                    continue;
                }
                String email = entry.trim().toLowerCase(Locale.ROOT);
                if (!EMAIL.matcher(email).matches()) {
                    throw new IllegalArgumentException("interviewerEmails: \"" + entry.trim() + "\" isn't an email address");
                }
                if (!email.equalsIgnoreCase(candidateEmail)) {
                    result.add(email);
                }
            }
        }
        return List.copyOf(result);
    }

    private static String description(String message) {
        String footer = "Join using the Google Meet link in this invitation. "
                + "If this time doesn't work for you, reply to this invitation.\n\n— CodeWalnut Talent Team";
        return message == null ? footer : message + "\n\n" + footer;
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Application not found"));
    }
}
