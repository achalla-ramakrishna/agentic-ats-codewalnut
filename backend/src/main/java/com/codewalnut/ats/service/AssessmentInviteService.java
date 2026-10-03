package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.dto.AssessmentDtos.AnswerReview;
import com.codewalnut.ats.dto.AssessmentDtos.CandidateQuestion;
import com.codewalnut.ats.dto.AssessmentDtos.InviteDetail;
import com.codewalnut.ats.dto.AssessmentDtos.InviteView;
import com.codewalnut.ats.dto.AssessmentDtos.MyTest;
import com.codewalnut.ats.dto.AssessmentDtos.NewResult;
import com.codewalnut.ats.dto.AssessmentDtos.RemindRequest;
import com.codewalnut.ats.dto.AssessmentDtos.SendResult;
import com.codewalnut.ats.dto.AssessmentDtos.SendTestRequest;
import com.codewalnut.ats.dto.AssessmentDtos.TakeTest;
import com.codewalnut.ats.dto.MessageDtos.MessageResponse;
import com.codewalnut.ats.dto.MessageDtos.PostMessageRequest;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.AssessmentQuestionRepository;
import com.codewalnut.ats.repository.AssessmentRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.MessageRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Sending tests to candidates, the candidate taking them, and the automatic score (ADR-0011).
 * The timer is enforced on the server; overdue tests are scored with what was saved. Scores are
 * a signal for people: nothing moves or rejects a candidate automatically.
 */
@Service
@RequiredArgsConstructor
public class AssessmentInviteService {

    /** Network delay allowance after the timer runs out. */
    static final Duration GRACE = Duration.ofSeconds(30);
    static final Duration NUDGE_AFTER = Duration.ofDays(2);
    private static final Set<AssessmentInvite.Status> ACTIVE = EnumSet.of(AssessmentInvite.Status.SENT, AssessmentInvite.Status.STARTED);
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final CandidateRepository candidateRepository;
    private final AssessmentService assessmentService;
    private final MessageService messageService;
    private final MessageRepository messageRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ---- staff ----

    /** Creates the invite and tells the candidate in their chat (and by email / WhatsApp if ticked). */
    @Transactional
    public SendResult send(AppUser actor, UUID applicationId, SendTestRequest request, String baseUrl) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        Application application = application(applicationId);
        Assessment assessment = assessmentRepository.findById(request.assessmentId())
                .orElseThrow(() -> new NotFoundException("Test not found"));
        if (assessment.getStatus() != Assessment.Status.READY) {
            throw new IllegalArgumentException("assessmentId: only tests marked ready can be sent");
        }
        if (!StringUtils.hasText(application.getCandidate().getEmail())) {
            throw new IllegalArgumentException("email: add the candidate's email first; they sign in with it to take the test");
        }
        boolean already = inviteRepository.findByApplicationIdOrderBySentAtDesc(applicationId).stream()
                .anyMatch(i -> i.getAssessment().getId().equals(assessment.getId()) && ACTIVE.contains(effective(i)));
        if (already) {
            throw new ConflictException(application.getCandidate().getName() + " already has this test open");
        }
        AssessmentInvite invite = inviteRepository.save(AssessmentInvite.builder()
                .assessment(assessment)
                .application(application)
                .status(AssessmentInvite.Status.SENT)
                .sentBy(actor.getEmail())
                .dueAt(Instant.now().plus(Duration.ofDays(request.dueDays())))
                .build());
        int questions = (int) questionRepository.countByAssessmentId(assessment.getId());
        String body = "Hi " + firstName(application.getCandidate().getName()) + ",\n\n"
                + "As the next step for " + application.getJob().getTitle() + ", please take the " + assessment.getTitle()
                + " test: " + questions + " question" + (questions == 1 ? "" : "s") + ", " + assessment.getDurationMinutes()
                + " minutes.\n\n"
                + (StringUtils.hasText(request.note()) ? request.note().strip() + "\n\n" : "")
                + "Open " + link(baseUrl, invite) + " and sign in with Google using " + application.getCandidate().getEmail()
                + ". The timer starts only when you click Start, so begin when you have " + assessment.getDurationMinutes()
                + " quiet minutes. Please finish it by " + DUE.format(invite.getDueAt()) + ".\n\nAll the best!";
        MessageResponse message = messageService.post(actor, applicationId, new PostMessageRequest(MessageChannel.CANDIDATE,
                body, "Your " + assessment.getTitle() + " test for " + application.getJob().getTitle(),
                request.sendEmail(), request.sendWhatsApp()), baseUrl);
        history(application, ApplicationEventType.TEST_SENT, "Test sent: " + assessment.getTitle()
                + " (due " + DUE.format(invite.getDueAt()) + ")", actor.getEmail());
        auditService.record(actor, AuditAction.ASSESSMENT_SENT, "AssessmentInvite", invite.getId(),
                Map.of("applicationId", applicationId, "assessmentId", assessment.getId()));
        return new SendResult(view(invite), message);
    }

    /** A friendly nudge for a test not yet started. An expired invite gets two more days. */
    @Transactional
    public SendResult remind(AppUser actor, UUID inviteId, RemindRequest request, String baseUrl) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        AssessmentInvite invite = invite(inviteId);
        AssessmentInvite.Status status = finalise(invite);
        if (status != AssessmentInvite.Status.SENT && status != AssessmentInvite.Status.EXPIRED) {
            throw new IllegalArgumentException("Only a test that hasn't been started can be reminded");
        }
        if (status == AssessmentInvite.Status.EXPIRED) {
            invite.setStatus(AssessmentInvite.Status.SENT);
            invite.setDueAt(Instant.now().plus(Duration.ofDays(2)));
        }
        invite.setReminderCount(invite.getReminderCount() + 1);
        invite.setLastRemindedAt(Instant.now());
        Application application = invite.getApplication();
        String body = "Hi " + firstName(application.getCandidate().getName()) + ", a quick reminder to take your "
                + invite.getAssessment().getTitle() + " test for " + application.getJob().getTitle() + " by "
                + DUE.format(invite.getDueAt()) + ". It takes " + invite.getAssessment().getDurationMinutes()
                + " minutes: " + link(baseUrl, invite) + " (sign in with Google using " + application.getCandidate().getEmail()
                + "). Reply here if you need more time.";
        MessageResponse message = messageService.post(actor, application.getId(), new PostMessageRequest(MessageChannel.CANDIDATE,
                body, "Reminder: your " + invite.getAssessment().getTitle() + " test", request.sendEmail(),
                request.sendWhatsApp()), baseUrl);
        auditService.record(actor, AuditAction.ASSESSMENT_REMINDED, "AssessmentInvite", inviteId,
                Map.of("reminders", invite.getReminderCount()));
        return new SendResult(view(invite), message);
    }

    @Transactional
    public InviteView cancel(AppUser actor, UUID inviteId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        AssessmentInvite invite = invite(inviteId);
        if (finalise(invite) == AssessmentInvite.Status.SUBMITTED) {
            throw new IllegalArgumentException("This test has already been submitted");
        }
        invite.setStatus(AssessmentInvite.Status.CANCELLED);
        auditService.record(actor, AuditAction.ASSESSMENT_CANCELLED, "AssessmentInvite", inviteId, Map.of());
        return view(invite);
    }

    @Transactional
    public List<InviteView> forApplication(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        application(applicationId);
        return inviteRepository.findByApplicationIdOrderBySentAtDesc(applicationId).stream()
                .peek(this::finalise)
                .map(this::view)
                .toList();
    }

    /** Every test sent in this opening, newest first. */
    @Transactional
    public List<InviteView> forJob(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        List<UUID> ids = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId).stream().map(Application::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return inviteRepository.findByApplicationIdInOrderBySentAtDesc(ids).stream()
                .peek(this::finalise)
                .map(this::view)
                .toList();
    }

    /** The candidate's answers next to the right ones, for staff. */
    @Transactional
    public InviteDetail detail(AppUser actor, UUID inviteId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        AssessmentInvite invite = invite(inviteId);
        finalise(invite);
        if (invite.getStatus() == AssessmentInvite.Status.SUBMITTED && accessPolicy.has(actor, Capability.MANAGE_JOBS)) {
            seen(invite, actor);
        }
        Map<UUID, List<String>> answers = answers(invite);
        List<AnswerReview> review = invite.getStatus() == AssessmentInvite.Status.SUBMITTED
                ? questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId()).stream()
                        .map(q -> new AnswerReview(q.getPosition(), q.getKind(), q.getPrompt(), q.getCode(),
                                assessmentService.options(q), answers.getOrDefault(q.getId(), List.of()),
                                assessmentService.correct(q), assessmentService.accepted(q), q.getPoints(),
                                assessmentService.earned(q, answers.get(q.getId())), q.getFigure(),
                                assessmentService.optionFigures(q), q.getSection()))
                        .toList()
                : List.of();
        return new InviteDetail(view(invite), review, sectionScores(review));
    }

    /**
     * Results nobody has looked at yet (the Tests badge and "New results"). Tests whose time ran
     * out are scored first, so a candidate who never pressed Submit still shows up.
     */
    @Transactional
    public List<NewResult> newResults(AppUser actor) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        inviteRepository.findByStatus(AssessmentInvite.Status.STARTED).forEach(this::finalise);
        return inviteRepository.findByStatusAndReviewedAtIsNullOrderBySubmittedAtDesc(AssessmentInvite.Status.SUBMITTED).stream()
                .limit(200)
                .map(i -> new NewResult(view(i), i.getApplication().getCandidate().getName(), i.getApplication().getJob().getId(),
                        i.getApplication().getJob().getTitle()))
                .toList();
    }

    /** Marks results as seen; ids that aren't new results are ignored. */
    @Transactional
    public int markSeen(AppUser actor, Collection<UUID> inviteIds) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        int n = 0;
        for (AssessmentInvite invite : inviteRepository.findAllById(inviteIds)) {
            if (invite.getStatus() == AssessmentInvite.Status.SUBMITTED && invite.getReviewedAt() == null) {
                seen(invite, actor);
                n++;
            }
        }
        return n;
    }

    private void seen(AssessmentInvite invite, AppUser actor) {
        if (invite.getReviewedAt() == null) {
            invite.setReviewedAt(Instant.now());
            invite.setReviewedBy(actor.getEmail());
        }
    }

    /** Submitted tests for these applications, newest first (for suggestions). */
    @Transactional(readOnly = true)
    public List<InviteView> latestSubmitted(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return inviteRepository.findByApplicationIdInOrderBySentAtDesc(applicationIds).stream()
                .filter(i -> i.getStatus() == AssessmentInvite.Status.SUBMITTED)
                .map(this::view)
                .toList();
    }

    /** One line per candidate for the assistant, e.g. "tests: Java basics 80% (pass mark 60%)". */
    @Transactional(readOnly = true)
    public Map<UUID, String> summaries(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<String>> lines = new LinkedHashMap<>();
        for (AssessmentInvite i : inviteRepository.findByApplicationIdInOrderBySentAtDesc(applicationIds)) {
            AssessmentInvite.Status status = effective(i);
            String line = i.getAssessment().getTitle() + " " + switch (status) {
                case SUBMITTED -> i.getPercent() + "% (pass mark " + i.getAssessment().getPassPercent() + "%)";
                case SENT -> "sent, not started";
                case STARTED -> "in progress";
                case EXPIRED -> "not taken by the due date";
                case CANCELLED -> "cancelled";
            };
            lines.computeIfAbsent(i.getApplication().getId(), k -> new ArrayList<>()).add(line);
        }
        return lines.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> "tests: " + String.join("; ", e.getValue())));
    }

    /** Score per section, in the order sections first appear; empty when no question has a section. */
    static List<com.codewalnut.ats.dto.AssessmentDtos.SectionScore> sectionScores(List<AnswerReview> review) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (AnswerReview a : review) {
            if (a.section() == null) {
                continue;
            }
            int[] t = totals.computeIfAbsent(a.section(), k -> new int[3]);
            t[0] += a.earned();
            t[1] += a.points();
            t[2]++;
        }
        return totals.entrySet().stream()
                .map(e -> new com.codewalnut.ats.dto.AssessmentDtos.SectionScore(e.getKey(), sectionLabel(e.getKey()),
                        e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    static String sectionLabel(String section) {
        try {
            return com.codewalnut.ats.domain.BankQuestion.Section.valueOf(section).getLabel();
        } catch (IllegalArgumentException e) {
            return section;
        }
    }

    // ---- the candidate ----

    @Transactional
    public List<MyTest> myTests(CandidateAccount account) {
        Candidate candidate = candidate(account);
        if (candidate == null) {
            return List.of();
        }
        return inviteRepository.findByApplicationCandidateIdOrderBySentAtDesc(candidate.getId()).stream()
                .filter(i -> i.getStatus() != AssessmentInvite.Status.CANCELLED)
                .peek(this::finalise)
                .map(this::mine)
                .toList();
    }

    /** Starts the timer. Starting again just returns the test in progress. */
    @Transactional
    public TakeTest start(CandidateAccount account, UUID inviteId) {
        AssessmentInvite invite = ownInvite(account, inviteId);
        AssessmentInvite.Status status = finalise(invite);
        if (status == AssessmentInvite.Status.SENT) {
            Instant now = Instant.now();
            invite.setStatus(AssessmentInvite.Status.STARTED);
            invite.setStartedAt(now);
            invite.setDeadlineAt(now.plus(Duration.ofMinutes(invite.getAssessment().getDurationMinutes())));
            auditService.recordAnonymous(account.getEmail(), AuditAction.ASSESSMENT_STARTED, Map.of("inviteId", inviteId));
        } else if (status != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException(closedMessage(status));
        }
        return take(invite);
    }

    @Transactional
    public TakeTest resume(CandidateAccount account, UUID inviteId) {
        AssessmentInvite invite = ownInvite(account, inviteId);
        AssessmentInvite.Status status = finalise(invite);
        if (status != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException(closedMessage(status));
        }
        return take(invite);
    }

    /** Saves answers as the candidate goes, so nothing is lost if the browser closes. */
    @Transactional
    public TakeTest save(CandidateAccount account, UUID inviteId, Map<UUID, List<String>> given) {
        AssessmentInvite invite = ownInvite(account, inviteId);
        AssessmentInvite.Status status = finalise(invite);
        if (status != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException(closedMessage(status));
        }
        merge(invite, given);
        return take(invite);
    }

    @Transactional
    public MyTest submit(CandidateAccount account, UUID inviteId, Map<UUID, List<String>> given) {
        AssessmentInvite invite = ownInvite(account, inviteId);
        AssessmentInvite.Status status = finalise(invite);
        if (status == AssessmentInvite.Status.SUBMITTED) {
            return mine(invite);
        }
        if (status != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException(closedMessage(status));
        }
        merge(invite, given);
        score(invite, Instant.now());
        return mine(invite);
    }

    // ---- scoring and state ----

    /** Brings an invite up to date: overdue tests are scored with what was saved; unstarted ones expire. */
    AssessmentInvite.Status finalise(AssessmentInvite invite) {
        Instant now = Instant.now();
        if (invite.getStatus() == AssessmentInvite.Status.STARTED && now.isAfter(invite.getDeadlineAt().plus(GRACE))) {
            score(invite, invite.getDeadlineAt());
        } else if (invite.getStatus() == AssessmentInvite.Status.SENT && now.isAfter(invite.getDueAt())) {
            invite.setStatus(AssessmentInvite.Status.EXPIRED);
        }
        return invite.getStatus();
    }

    private void score(AssessmentInvite invite, Instant submittedAt) {
        Map<UUID, List<String>> answers = answers(invite);
        int score = 0;
        int max = 0;
        for (AssessmentQuestion q : questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId())) {
            max += q.getPoints();
            score += assessmentService.earned(q, answers.get(q.getId()));
        }
        invite.setScore(score);
        invite.setMaxScore(max);
        invite.setPercent(max == 0 ? 0 : Math.round(score * 100f / max));
        invite.setStatus(AssessmentInvite.Status.SUBMITTED);
        invite.setSubmittedAt(submittedAt);
        history(invite.getApplication(), ApplicationEventType.TEST_SUBMITTED, "Test submitted: " + invite.getAssessment().getTitle()
                + " — " + invite.getPercent() + "% (" + score + "/" + max + "; pass mark " + invite.getAssessment().getPassPercent() + "%)",
                invite.getApplication().getCandidate().getEmail());
        auditService.recordAnonymous(invite.getApplication().getCandidate().getEmail(), AuditAction.ASSESSMENT_SUBMITTED,
                Map.of("inviteId", invite.getId(), "percent", invite.getPercent()));
        resultNote(invite, submittedAt);
    }

    /** A note in the candidate's team chat, so the result is on record where the team talks about them. */
    private void resultNote(AssessmentInvite invite, Instant submittedAt) {
        Assessment a = invite.getAssessment();
        boolean passed = invite.getPercent() >= a.getPassPercent();
        boolean timedOut = invite.getDeadlineAt() != null && !submittedAt.isBefore(invite.getDeadlineAt());
        String body = "Test result: " + invite.getApplication().getCandidate().getName() + " scored " + invite.getPercent() + "% ("
                + invite.getScore() + "/" + invite.getMaxScore() + ") on " + a.getTitle() + " — "
                + (passed ? "passed" : "below the pass mark") + " (pass mark " + a.getPassPercent() + "%)."
                + (timedOut ? " Time ran out, so the answers saved by then were scored." : "")
                + " Sent by " + invite.getSentBy() + ". See the answers under Tests.";
        messageRepository.save(Message.builder()
                .application(invite.getApplication())
                .channel(MessageChannel.TEAM)
                .authorType(MessageAuthorType.STAFF)
                .authorEmail(SYSTEM_AUTHOR)
                .authorName("CodeWalnut ATS")
                .body(body)
                .emailed(false)
                .build());
    }

    /** Author of notes the app writes itself. */
    static final String SYSTEM_AUTHOR = "system@codewalnut-ats";

    private AssessmentInvite.Status effective(AssessmentInvite invite) {
        Instant now = Instant.now();
        if (invite.getStatus() == AssessmentInvite.Status.SENT && now.isAfter(invite.getDueAt())) {
            return AssessmentInvite.Status.EXPIRED;
        }
        return invite.getStatus();
    }

    private void merge(AssessmentInvite invite, Map<UUID, List<String>> given) {
        if (given == null || given.isEmpty()) {
            return;
        }
        Set<UUID> known = questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId()).stream()
                .map(AssessmentQuestion::getId).collect(Collectors.toSet());
        Map<UUID, List<String>> answers = new LinkedHashMap<>(answers(invite));
        given.forEach((id, values) -> {
            if (id != null && known.contains(id)) {
                answers.put(id, values == null ? List.of() : values.stream()
                        .filter(v -> v != null).map(v -> v.length() > 500 ? v.substring(0, 500) : v).limit(10).toList());
            }
        });
        try {
            invite.setAnswersJson(objectMapper.writeValueAsString(answers));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<UUID, List<String>> answers(AssessmentInvite invite) {
        if (invite.getAnswersJson() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(invite.getAnswersJson(), new TypeReference<Map<UUID, List<String>>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private TakeTest take(AssessmentInvite invite) {
        List<CandidateQuestion> questions = questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId())
                .stream()
                .map(q -> new CandidateQuestion(q.getId(), q.getPosition(), q.getKind(), q.getPrompt(), q.getCode(),
                        assessmentService.options(q), q.getPoints(), q.getFigure(), assessmentService.optionFigures(q), q.getSection()))
                .toList();
        long secondsLeft = Math.max(0, Duration.between(Instant.now(), invite.getDeadlineAt()).getSeconds());
        return new TakeTest(mine(invite), questions, answers(invite), secondsLeft);
    }

    private InviteView view(AssessmentInvite i) {
        AssessmentInvite.Status status = effective(i);
        Instant lastContact = i.getLastRemindedAt() != null ? i.getLastRemindedAt() : i.getSentAt();
        boolean nudge = status == AssessmentInvite.Status.SENT && lastContact != null
                && lastContact.isBefore(Instant.now().minus(NUDGE_AFTER));
        return new InviteView(i.getId(), i.getApplication().getId(), i.getAssessment().getId(), i.getAssessment().getTitle(),
                i.getAssessment().getCategory(), status, i.getSentBy(), i.getSentAt(), i.getDueAt(), i.getStartedAt(),
                i.getSubmittedAt(), i.getScore(), i.getMaxScore(), i.getPercent(),
                i.getPercent() == null ? null : i.getPercent() >= i.getAssessment().getPassPercent(),
                i.getAssessment().getPassPercent(), i.getReminderCount(), i.getLastRemindedAt(), nudge || status == AssessmentInvite.Status.EXPIRED,
                status == AssessmentInvite.Status.SUBMITTED && i.getReviewedAt() == null);
    }

    private MyTest mine(AssessmentInvite i) {
        Assessment a = i.getAssessment();
        return new MyTest(i.getId(), a.getTitle(), a.getCategory(), a.getDescription(), i.getApplication().getJob().getTitle(),
                (int) questionRepository.countByAssessmentId(a.getId()), a.getDurationMinutes(), effective(i), i.getDueAt(),
                i.getStartedAt(), i.getDeadlineAt(), i.getSubmittedAt());
    }

    private static String closedMessage(AssessmentInvite.Status status) {
        return switch (status) {
            case SUBMITTED -> "You've already submitted this test. Thank you!";
            case EXPIRED -> "This test is past its due date. Message the CodeWalnut team if you need more time.";
            case CANCELLED -> "This test was withdrawn by CodeWalnut.";
            default -> "This test isn't open.";
        };
    }

    private void history(Application application, ApplicationEventType type, String note, String actorEmail) {
        application.setUpdatedAt(Instant.now());
        eventRepository.save(ApplicationEvent.builder()
                .application(application).type(type).note(note).actorEmail(actorEmail).build());
    }

    private AssessmentInvite ownInvite(CandidateAccount account, UUID inviteId) {
        AssessmentInvite invite = inviteRepository.findById(inviteId).orElseThrow(() -> new NotFoundException("Test not found"));
        String email = invite.getApplication().getCandidate().getEmail();
        if (email == null || !email.equalsIgnoreCase(account.getEmail()) || invite.getStatus() == AssessmentInvite.Status.CANCELLED) {
            // Same answer as a missing test, so ids can't be probed.
            throw new NotFoundException("Test not found. Make sure you signed in with the email address the test was sent to.");
        }
        return invite;
    }

    private Candidate candidate(CandidateAccount account) {
        return candidateRepository.findByEmail(account.getEmail().toLowerCase(Locale.ROOT)).orElse(null);
    }

    private AssessmentInvite invite(UUID id) {
        return inviteRepository.findById(id).orElseThrow(() -> new NotFoundException("Test not found"));
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Candidate entry not found"));
    }

    static String link(String baseUrl, AssessmentInvite invite) {
        return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "tests/" + invite.getId();
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "there" : name.strip().split("\\s+")[0];
    }
}
