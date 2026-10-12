package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarNotConnectedException;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.BookingLink;
import com.codewalnut.ats.domain.InterviewSlot;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.BookingDtos.BookingPage;
import com.codewalnut.ats.dto.BookingDtos.CreateSlotsRequest;
import com.codewalnut.ats.dto.BookingDtos.CreateSlotsResult;
import com.codewalnut.ats.dto.BookingDtos.LinksResponse;
import com.codewalnut.ats.dto.BookingDtos.PublicSlot;
import com.codewalnut.ats.dto.BookingDtos.SendInvitesResult;
import com.codewalnut.ats.dto.BookingDtos.SlotView;
import com.codewalnut.ats.dto.BookingDtos.SlotsResponse;
import com.codewalnut.ats.dto.BookingDtos.Window;
import com.codewalnut.ats.dto.InterviewDtos.ScheduleInterviewRequest;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.BookingLinkRepository;
import com.codewalnut.ats.repository.InterviewSlotRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Interview self-booking (PIPE-24): a recruiter offers slots for an opening and emails each candidate a
 * personal link; the candidate picks a slot (no sign-in); the recruiter then sends all the calendar
 * invites in one go through the normal interview scheduling, while connected to Google.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    static final int MAX_SLOTS_PER_REQUEST = 500;
    /** Candidates can't book a slot starting sooner than this, so the recruiter has time to send the invite. */
    static final Duration NOTICE = Duration.ofHours(2);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

    private final AccessPolicy accessPolicy;
    private final InterviewSlotRepository slotRepository;
    private final BookingLinkRepository linkRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final InterviewService interviewService;
    private final TrackerService trackerService;
    private final AuditService auditService;

    // ---- recruiter ----

    @Transactional
    public CreateSlotsResult createSlots(AppUser actor, UUID jobId, CreateSlotsRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        trackerService.openJob(jobId);
        ZoneId zone = zone(request.timeZone());
        if (request.to().isBefore(request.from())) {
            throw new IllegalArgumentException("to: the last day must be on or after the first");
        }
        if (Duration.between(request.from().atStartOfDay(), request.to().atStartOfDay()).toDays() > 31) {
            throw new IllegalArgumentException("to: offer at most a month of slots at a time");
        }
        Duration length = Duration.ofMinutes(request.durationMinutes());
        String interviewers = request.interviewerEmails() == null ? null
                : String.join(",", request.interviewerEmails().stream().map(String::strip).filter(StringUtils::hasText).toList());
        Instant now = Instant.now();
        int created = 0;
        int skipped = 0;
        for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
            if (request.weekdaysOnly() && (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                continue;
            }
            for (Window w : request.windows()) {
                LocalTime start = time(w.start());
                LocalTime end = time(w.end());
                if (!end.isAfter(start)) {
                    throw new IllegalArgumentException("windows: each window must end after it starts (" + w.start() + "–" + w.end() + ")");
                }
                for (LocalTime t = start; !t.plus(length).isAfter(end) && !t.plus(length).equals(LocalTime.MIDNIGHT); t = t.plus(length)) {
                    Instant at = ZonedDateTime.of(day, t, zone).toInstant();
                    if (at.isBefore(now) || slotRepository.existsByJobIdAndStartAt(jobId, at)) {
                        skipped++;
                        continue;
                    }
                    if (created >= MAX_SLOTS_PER_REQUEST) {
                        throw new IllegalArgumentException("That is more than " + MAX_SLOTS_PER_REQUEST + " slots; choose fewer days or shorter windows");
                    }
                    slotRepository.save(InterviewSlot.builder().jobId(jobId).startAt(at).endAt(at.plus(length))
                            .timeZone(zone.getId()).interviewerEmails(StringUtils.hasText(interviewers) ? interviewers : null)
                            .createdBy(actor.getEmail()).build());
                    created++;
                }
            }
        }
        auditService.record(actor, AuditAction.JOB_UPDATED, "JobOpening", jobId, Map.of("interviewSlotsAdded", created));
        return new CreateSlotsResult(created, skipped, slots(actor, jobId));
    }

    @Transactional(readOnly = true)
    public SlotsResponse slots(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        List<InterviewSlot> slots = slotRepository.findByJobIdOrderByStartAtAsc(jobId);
        Map<UUID, String> names = new LinkedHashMap<>();
        List<UUID> booked = slots.stream().map(InterviewSlot::getApplicationId).filter(java.util.Objects::nonNull).toList();
        applicationRepository.findAllById(booked).forEach(a -> names.put(a.getId(), a.getCandidate().getName()));
        Instant now = Instant.now();
        List<SlotView> views = slots.stream()
                // Past slots nobody booked are noise.
                .filter(s -> s.getApplicationId() != null || s.getEndAt().isAfter(now))
                .map(s -> new SlotView(s.getId(), s.getStartAt(), s.getEndAt(), s.getTimeZone(), s.getApplicationId(),
                        names.get(s.getApplicationId()), s.getBookedAt(), s.getInterviewId()))
                .toList();
        int free = (int) views.stream().filter(v -> v.applicationId() == null).count();
        int scheduled = (int) views.stream().filter(v -> v.interviewId() != null).count();
        return new SlotsResponse(views, free, views.size() - free - scheduled, scheduled);
    }

    /** Removes a free slot; a booked one is kept so nobody loses their time without being told. */
    @Transactional
    public SlotsResponse deleteSlot(AppUser actor, UUID slotId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        InterviewSlot slot = slotRepository.findById(slotId).orElseThrow(() -> new NotFoundException("Slot not found"));
        if (slot.getApplicationId() != null) {
            throw new IllegalArgumentException("This slot is booked. Reschedule or cancel that interview with the candidate instead.");
        }
        slotRepository.delete(slot);
        return slots(actor, slot.getJobId());
    }

    /** Each candidate's personal booking link, created on first use. */
    @Transactional
    public LinksResponse links(AppUser actor, List<UUID> applicationIds, String baseUrl) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        Map<UUID, String> links = new LinkedHashMap<>();
        Map<UUID, String> problems = new LinkedHashMap<>();
        Map<UUID, Boolean> jobHasSlots = new LinkedHashMap<>();
        Instant earliest = Instant.now().plus(NOTICE);
        for (Application a : applicationRepository.findAllById(applicationIds)) {
            UUID jobId = a.getJob().getId();
            boolean open = jobHasSlots.computeIfAbsent(jobId, id -> slotRepository.findByJobIdOrderByStartAtAsc(id).stream()
                    .anyMatch(s -> s.getApplicationId() == null && s.getStartAt().isAfter(earliest)));
            if (!open) {
                problems.put(a.getId(), "no free interview slots in " + a.getJob().getTitle() + " yet; add slots first");
                continue;
            }
            BookingLink link = linkRepository.findByApplicationId(a.getId()).orElseGet(() -> linkRepository.save(
                    BookingLink.builder().token(PublicLinks.newSlug() + PublicLinks.newSlug() + PublicLinks.newSlug())
                            .applicationId(a.getId()).build()));
            links.put(a.getId(), baseUrl + "book/" + link.getToken());
        }
        return new LinksResponse(links, problems);
    }

    /**
     * Sends the calendar invite (Google Meet) for every booked slot of the opening that has none yet,
     * through the normal interview scheduling. Needs the recruiter's Google connection.
     */
    public SendInvitesResult sendInvites(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Instant now = Instant.now();
        List<InterviewSlot> pending = slotRepository.findByJobIdOrderByStartAtAsc(jobId).stream()
                .filter(s -> s.getApplicationId() != null && s.getInterviewId() == null && s.getStartAt().isAfter(now))
                .toList();
        int scheduled = 0;
        List<String> failed = new ArrayList<>();
        for (InterviewSlot slot : pending) {
            Application application = applicationRepository.findById(slot.getApplicationId()).orElse(null);
            if (application == null) {
                continue;
            }
            try {
                List<String> interviewers = slot.getInterviewerEmails() == null ? List.of()
                        : Arrays.stream(slot.getInterviewerEmails().split(",")).filter(StringUtils::hasText).toList();
                var interview = interviewService.schedule(actor, application.getId(), new ScheduleInterviewRequest(
                        null, slot.getStartAt(), (int) Duration.between(slot.getStartAt(), slot.getEndAt()).toMinutes(),
                        slot.getTimeZone(), interviewers, null));
                slot.setInterviewId(interview.id());
                slotRepository.save(slot);
                scheduled++;
            } catch (CalendarNotConnectedException e) {
                throw e; // the page asks to connect Google, then the recruiter clicks again
            } catch (RuntimeException e) {
                failed.add(application.getCandidate().getName() + ": " + e.getMessage());
            }
        }
        return new SendInvitesResult(scheduled, failed);
    }

    // ---- candidate (public, by token) ----

    @Transactional(readOnly = true)
    public BookingPage page(String token) {
        Application application = applicationFor(token);
        return page(application);
    }

    private BookingPage page(Application application) {
        UUID jobId = application.getJob().getId();
        List<InterviewSlot> all = slotRepository.findByJobIdOrderByStartAtAsc(jobId);
        Optional<InterviewSlot> mine = all.stream().filter(s -> application.getId().equals(s.getApplicationId())).findFirst();
        Instant earliest = Instant.now().plus(NOTICE);
        List<PublicSlot> free = all.stream()
                .filter(s -> s.getApplicationId() == null && s.getStartAt().isAfter(earliest))
                .map(BookingService::view).toList();
        String zone = mine.map(InterviewSlot::getTimeZone)
                .orElse(all.isEmpty() ? "Asia/Kolkata" : all.get(0).getTimeZone());
        int minutes = all.isEmpty() ? 30 : (int) Duration.between(all.get(0).getStartAt(), all.get(0).getEndAt()).toMinutes();
        return new BookingPage(firstName(application.getCandidate().getName()), application.getJob().getTitle(), zone, minutes,
                mine.map(BookingService::view).orElse(null), mine.map(s -> s.getInterviewId() != null).orElse(false), free);
    }

    /** Books a slot, releasing the candidate's previous one, until the invite has been sent. */
    @Transactional
    public BookingPage book(String token, UUID slotId) {
        Application application = applicationFor(token);
        InterviewSlot slot = slotRepository.findById(slotId)
                .filter(s -> s.getJobId().equals(application.getJob().getId()))
                .orElseThrow(() -> new IllegalArgumentException("That time is no longer available. Please pick another."));
        if (slot.getStartAt().isBefore(Instant.now().plus(NOTICE))) {
            throw new IllegalArgumentException("That time is too soon. Please pick a later one.");
        }
        Optional<InterviewSlot> current = slotRepository.findByApplicationId(application.getId());
        if (current.isPresent()) {
            if (current.get().getId().equals(slotId)) {
                return page(application);
            }
            if (current.get().getInterviewId() != null) {
                throw new IllegalArgumentException(
                        "Your interview is already scheduled; the calendar invite has the details. Reply to our email to change it.");
            }
            InterviewSlot previous = current.get();
            previous.setApplicationId(null);
            previous.setBookedAt(null);
            slotRepository.saveAndFlush(previous);
        }
        if (slotRepository.book(slotId, application.getId(), Instant.now()) == 0) {
            throw new IllegalArgumentException("Someone just booked that time. Please pick another.");
        }
        ZonedDateTime when = slot.getStartAt().atZone(zone(slot.getTimeZone()));
        application.setUpdatedAt(Instant.now());
        eventRepository.save(ApplicationEvent.builder()
                .application(application)
                .type(ApplicationEventType.SLOT_BOOKED)
                .note("Picked " + WHEN.format(when) + " (" + slot.getTimeZone() + ")")
                .actorEmail(application.getCandidate().getEmail() == null ? "candidate" : application.getCandidate().getEmail())
                .build());
        return page(application);
    }

    // ---- helpers ----

    private Application applicationFor(String token) {
        if (token == null || token.length() > 64) {
            throw new NotFoundException("This booking link isn't valid.");
        }
        BookingLink link = linkRepository.findById(token).orElseThrow(() -> new NotFoundException("This booking link isn't valid."));
        Application application = applicationRepository.findById(link.getApplicationId())
                .orElseThrow(() -> new NotFoundException("This booking link isn't valid."));
        if (EnumSet.of(Stage.REJECTED, Stage.WITHDRAWN, Stage.JOINED).contains(application.getStage().current())) {
            throw new NotFoundException("This booking link has expired.");
        }
        return application;
    }

    private static PublicSlot view(InterviewSlot s) {
        return new PublicSlot(s.getId(), s.getStartAt(), s.getEndAt());
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id.trim());
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("timeZone: unknown time zone");
        }
    }

    private static LocalTime time(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("windows: use times like 10:00");
        }
    }

    private static String firstName(String name) {
        return name == null ? "" : name.strip().split("\\s+")[0];
    }
}
