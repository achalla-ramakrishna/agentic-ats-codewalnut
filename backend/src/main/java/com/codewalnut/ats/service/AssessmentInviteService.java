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
import com.codewalnut.ats.dto.AssessmentDtos.Activity;
import com.codewalnut.ats.dto.AssessmentDtos.ActivityRequest;
import com.codewalnut.ats.dto.AssessmentDtos.AnswerReview;
import com.codewalnut.ats.dto.AssessmentDtos.CaseResult;
import com.codewalnut.ats.dto.AssessmentDtos.CodeResult;
import com.codewalnut.ats.dto.AssessmentDtos.CodingSpec;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final CodingSpecs codingSpecs;
    private final ApplicationEventPublisher events;

    /** Sample runs a candidate may make per coding question, and per test. */
    static final int RUNS_PER_QUESTION = 30;
    static final int RUNS_PER_TEST = 150;
    /** Grading attempts while the sandbox is down (about one a minute) before giving up. */
    static final int MAX_GRADING_ATTEMPTS = 30;

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
        Map<UUID, CodeResult> code = codeResults(invite);
        List<AnswerReview> review = invite.getStatus() == AssessmentInvite.Status.SUBMITTED
                ? questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId()).stream()
                        .map(q -> new AnswerReview(q.getPosition(), q.getKind(), q.getPrompt(), q.getCode(),
                                assessmentService.options(q), answers.getOrDefault(q.getId(), List.of()),
                                assessmentService.correct(q), assessmentService.accepted(q), q.getPoints(),
                                earned(q, answers, code), q.getFigure(),
                                assessmentService.optionFigures(q), q.getSection(), staffCode(q, answers, code)))
                        .toList()
                : List.of();
        return new InviteDetail(view(invite), review, sectionScores(review), activity(invite));
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
                case SUBMITTED -> i.getPercent() == null ? "submitted, code still being graded"
                        : i.getPercent() + "% (pass mark " + i.getAssessment().getPassPercent() + "%)";
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

    /** "QUANT" → "Numerical ability"; "JAVA:PRACTICAL" (role tests) → "Java · Applied". */
    static String sectionLabel(String section) {
        int colon = section.indexOf(':');
        try {
            if (colon > 0) {
                return com.codewalnut.ats.bank.Presets.areaName(Assessment.Category.valueOf(section.substring(0, colon))) + " · "
                        + com.codewalnut.ats.domain.BankQuestion.Section.valueOf(section.substring(colon + 1)).getLabel();
            }
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
        List<AssessmentQuestion> questions = questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId());
        Map<UUID, List<String>> answers = answers(invite);
        invite.setStatus(AssessmentInvite.Status.SUBMITTED);
        invite.setSubmittedAt(submittedAt);
        boolean hasCode = questions.stream().anyMatch(q -> q.getKind() == AssessmentQuestion.Kind.CODING);
        boolean toRun = questions.stream().anyMatch(q -> q.getKind() == AssessmentQuestion.Kind.CODING && source(answers.get(q.getId())) != null);
        if (toRun) {
            // Code is run against the hidden tests after this commits; the result is final once graded.
            total(invite, questions);
            invite.setPercent(null);
            invite.setGrading(AssessmentInvite.Grading.PENDING);
            invite.setGradingAttempts(0);
            history(invite.getApplication(), ApplicationEventType.TEST_SUBMITTED, "Test submitted: " + invite.getAssessment().getTitle()
                    + " — grading the code", invite.getApplication().getCandidate().getEmail());
            auditService.recordAnonymous(invite.getApplication().getCandidate().getEmail(), AuditAction.ASSESSMENT_SUBMITTED,
                    Map.of("inviteId", invite.getId(), "grading", "pending"));
            events.publishEvent(new CodeGradingRequested(invite.getId()));
            return;
        }
        if (hasCode) {
            invite.setGrading(AssessmentInvite.Grading.DONE); // coding questions left blank score 0
        }
        finish(invite, questions, submittedAt);
    }

    /** Final score, history, audit and the team-chat note, once every answer (code included) is scored. */
    private void finish(AssessmentInvite invite, List<AssessmentQuestion> questions, Instant submittedAt) {
        total(invite, questions);
        int score = invite.getScore();
        int max = invite.getMaxScore();
        history(invite.getApplication(), ApplicationEventType.TEST_SUBMITTED, "Test submitted: " + invite.getAssessment().getTitle()
                + " — " + invite.getPercent() + "% (" + score + "/" + max + "; pass mark " + invite.getAssessment().getPassPercent() + "%)",
                invite.getApplication().getCandidate().getEmail());
        auditService.recordAnonymous(invite.getApplication().getCandidate().getEmail(), AuditAction.ASSESSMENT_SUBMITTED,
                Map.of("inviteId", invite.getId(), "percent", invite.getPercent()));
        resultNote(invite, submittedAt);
    }

    private void total(AssessmentInvite invite, List<AssessmentQuestion> questions) {
        Map<UUID, List<String>> answers = answers(invite);
        Map<UUID, CodeResult> code = codeResults(invite);
        int score = 0;
        int max = 0;
        for (AssessmentQuestion q : questions) {
            max += q.getPoints();
            score += earned(q, answers, code);
        }
        invite.setScore(score);
        invite.setMaxScore(max);
        invite.setPercent(max == 0 ? 0 : Math.round(score * 100f / max));
    }

    private int earned(AssessmentQuestion q, Map<UUID, List<String>> answers, Map<UUID, CodeResult> code) {
        if (q.getKind() == AssessmentQuestion.Kind.CODING) {
            CodeResult r = code.get(q.getId());
            return r == null ? 0 : r.earned();
        }
        return assessmentService.earned(q, answers.get(q.getId()));
    }

    // ---- code grading (ADR-0016) ----

    /** One coding answer to run: everything the grader needs, read in one transaction. */
    public record CodeWork(UUID questionId, int points, String language, String source, CodingSpec spec, List<TestCase> tests) {}

    /** The coding answers of a test waiting to be graded; empty when it isn't (any more). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CodeWork> gradingWork(UUID inviteId) {
        AssessmentInvite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.getGrading() != AssessmentInvite.Grading.PENDING) {
            return List.of();
        }
        Map<UUID, List<String>> answers = answers(invite);
        List<CodeWork> work = new ArrayList<>();
        for (AssessmentQuestion q : questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId())) {
            if (q.getKind() != AssessmentQuestion.Kind.CODING) {
                continue;
            }
            CodingSpec spec = codingSpecs.spec(q.getCodingJson());
            List<String> given = answers.get(q.getId());
            work.add(new CodeWork(q.getId(), q.getPoints(), language(given), source(given), spec, codingSpecs.allTests(spec, q.getAnswerJson())));
        }
        return work;
    }

    /** Stores the graded code, works out the final score and posts the result, if still pending. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeGrading(UUID inviteId, Map<UUID, CodeResult> results) {
        AssessmentInvite invite = invite(inviteId);
        if (invite.getGrading() != AssessmentInvite.Grading.PENDING) {
            return;
        }
        invite.setCodeResultsJson(write(results));
        invite.setGrading(AssessmentInvite.Grading.DONE);
        finish(invite, questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId()), invite.getSubmittedAt());
    }

    /** The sandbox was unavailable: try again later, or give up and tell the team after many attempts. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void gradingFailed(UUID inviteId, String reason) {
        AssessmentInvite invite = invite(inviteId);
        if (invite.getGrading() != AssessmentInvite.Grading.PENDING) {
            return;
        }
        invite.setGradingAttempts(invite.getGradingAttempts() + 1);
        if (invite.getGradingAttempts() >= MAX_GRADING_ATTEMPTS) {
            invite.setGrading(AssessmentInvite.Grading.FAILED);
            teamNote(invite, "The code in " + invite.getApplication().getCandidate().getName() + "'s " + invite.getAssessment().getTitle()
                    + " test couldn't be graded: the code runner isn't reachable (" + reason + "). Once it's back, open the test "
                    + "under Tests and click Grade again.");
        }
    }

    /** Ids of tests waiting for their code to be graded. */
    @Transactional(readOnly = true)
    public List<UUID> pendingGrading() {
        return inviteRepository.findByGrading(AssessmentInvite.Grading.PENDING).stream().map(AssessmentInvite::getId).toList();
    }

    /** Staff: grade the code again (after the runner was down, or a test case was fixed in a copy). */
    @Transactional
    public InviteView regrade(AppUser actor, UUID inviteId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        AssessmentInvite invite = invite(inviteId);
        if (invite.getStatus() != AssessmentInvite.Status.SUBMITTED || invite.getGrading() == null) {
            throw new IllegalArgumentException("Only a submitted test with coding questions can be graded again");
        }
        invite.setGrading(AssessmentInvite.Grading.PENDING);
        invite.setGradingAttempts(0);
        invite.setCodeResultsJson(null);
        invite.setPercent(null);
        auditService.record(actor, AuditAction.ASSESSMENT_REGRADED, "AssessmentInvite", inviteId, Map.of());
        events.publishEvent(new CodeGradingRequested(inviteId));
        return view(invite);
    }

    /** What a candidate's Run needs: the question's spec, after checking the test is open and runs are left. */
    public record RunTicket(CodingSpec spec, int runsLeft) {}

    @Transactional
    public RunTicket startRun(CandidateAccount account, UUID inviteId, UUID questionId, String language) {
        AssessmentInvite invite = ownInvite(account, inviteId);
        AssessmentInvite.Status status = finalise(invite);
        if (status != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException(closedMessage(status));
        }
        AssessmentQuestion q = questionRepository.findById(questionId)
                .filter(x -> x.getAssessmentId().equals(invite.getAssessment().getId()) && x.getKind() == AssessmentQuestion.Kind.CODING)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        CodingSpec spec = codingSpecs.spec(q.getCodingJson());
        if (!spec.languages().contains(language)) {
            throw new IllegalArgumentException("language: choose one of " + String.join(", ", spec.languages()));
        }
        Map<String, Object> activity = activityMap(invite);
        @SuppressWarnings("unchecked")
        Map<String, Object> runs = (Map<String, Object>) activity.computeIfAbsent("runs", k -> new LinkedHashMap<String, Object>());
        int forQuestion = ((Number) runs.getOrDefault(questionId.toString(), 0)).intValue();
        int total = runs.values().stream().mapToInt(v -> ((Number) v).intValue()).sum();
        if (forQuestion >= RUNS_PER_QUESTION || total >= RUNS_PER_TEST) {
            throw new IllegalArgumentException("You've used all your sample runs for this question. You can still edit and submit your code.");
        }
        runs.put(questionId.toString(), forQuestion + 1);
        invite.setActivityJson(write(activity));
        return new RunTicket(spec, Math.min(RUNS_PER_QUESTION - forQuestion - 1, RUNS_PER_TEST - total - 1));
    }

    /** Browser signals while the test is open; totals only ever go up, so a resend never double counts. */
    @Transactional
    public void activity(CandidateAccount account, UUID inviteId, ActivityRequest request) {
        AssessmentInvite invite = ownInvite(account, inviteId);
        if (finalise(invite) != AssessmentInvite.Status.STARTED) {
            return;
        }
        Map<String, Object> activity = activityMap(invite);
        for (Map.Entry<String, Integer> e : Map.of("tabSwitches", request.tabSwitches(), "pastes", request.pastes(),
                "pastedChars", request.pastedChars()).entrySet()) {
            int old = ((Number) activity.getOrDefault(e.getKey(), 0)).intValue();
            activity.put(e.getKey(), Math.max(old, e.getValue()));
        }
        invite.setActivityJson(write(activity));
    }

    private Activity activity(AssessmentInvite invite) {
        Map<String, Object> a = activityMap(invite);
        @SuppressWarnings("unchecked")
        Map<String, Object> runs = (Map<String, Object>) a.getOrDefault("runs", Map.of());
        return new Activity(((Number) a.getOrDefault("tabSwitches", 0)).intValue(), ((Number) a.getOrDefault("pastes", 0)).intValue(),
                ((Number) a.getOrDefault("pastedChars", 0)).intValue(), runs.values().stream().mapToInt(v -> ((Number) v).intValue()).sum());
    }

    private Map<String, Object> activityMap(AssessmentInvite invite) {
        if (invite.getActivityJson() == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(invite.getActivityJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<UUID, CodeResult> codeResults(AssessmentInvite invite) {
        if (invite.getCodeResultsJson() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(invite.getCodeResultsJson(), new TypeReference<Map<UUID, CodeResult>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The graded code with each hidden test's input and expected output filled in, for staff. */
    private CodeResult staffCode(AssessmentQuestion q, Map<UUID, List<String>> answers, Map<UUID, CodeResult> code) {
        if (q.getKind() != AssessmentQuestion.Kind.CODING) {
            return null;
        }
        List<String> given = answers.get(q.getId());
        CodeResult r = code.get(q.getId());
        if (r == null) {
            return new CodeResult(language(given), source(given), 0, 0, 0, null, List.of());
        }
        List<TestCase> tests = codingSpecs.allTests(codingSpecs.spec(q.getCodingJson()), q.getAnswerJson());
        List<CaseResult> cases = new ArrayList<>();
        for (int i = 0; i < r.cases().size(); i++) {
            CaseResult c = r.cases().get(i);
            TestCase t = i < tests.size() ? tests.get(i) : null;
            cases.add(new CaseResult(c.sample(), c.passed(), c.status(), c.output(), c.error(), c.timeSeconds(), c.memoryKb(),
                    t == null ? null : clip(t.input(), 2000), t == null ? null : clip(t.output(), 2000)));
        }
        return new CodeResult(r.language(), r.source(), r.passed(), r.total(), r.earned(), r.compileOutput(), cases);
    }

    static String language(List<String> given) {
        return given == null || given.isEmpty() ? null : given.get(0);
    }

    /** The submitted source, or null when the candidate left the question blank. */
    static String source(List<String> given) {
        return given == null || given.size() < 2 || given.get(1) == null || given.get(1).isBlank() ? null : given.get(1);
    }

    static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "\n…";
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void teamNote(AssessmentInvite invite, String body) {
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

    /** A note in the candidate's team chat, so the result is on record where the team talks about them. */
    private void resultNote(AssessmentInvite invite, Instant submittedAt) {
        Assessment a = invite.getAssessment();
        boolean passed = invite.getPercent() >= a.getPassPercent();
        boolean timedOut = invite.getDeadlineAt() != null && !submittedAt.isBefore(invite.getDeadlineAt());
        String body = "Test result: " + invite.getApplication().getCandidate().getName() + " scored " + invite.getPercent() + "% ("
                + invite.getScore() + "/" + invite.getMaxScore() + ") on " + a.getTitle() + " — "
                + (passed ? "passed" : "below the pass mark") + " (pass mark " + a.getPassPercent() + "%)."
                + (timedOut ? " Time ran out, so the answers saved by then were scored." : "")
                + codeSummary(invite)
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

    /** e.g. " Code: 7/10 tests passed on Two sum (Python)." */
    private String codeSummary(AssessmentInvite invite) {
        Map<UUID, CodeResult> code = codeResults(invite);
        if (code.isEmpty()) {
            return "";
        }
        return " Code: " + code.values().stream()
                .map(r -> r.passed() + "/" + r.total() + " tests passed"
                        + (r.language() == null ? "" : " (" + CodingSpecs.LANGUAGE_NAMES.getOrDefault(r.language(), r.language()) + ")"))
                .collect(Collectors.joining("; ")) + ".";
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
        Map<UUID, AssessmentQuestion.Kind> known = questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId())
                .stream().collect(Collectors.toMap(AssessmentQuestion::getId, AssessmentQuestion::getKind));
        Map<UUID, List<String>> answers = new LinkedHashMap<>(answers(invite));
        given.forEach((id, values) -> {
            if (id == null || !known.containsKey(id)) {
                return;
            }
            if (known.get(id) == AssessmentQuestion.Kind.CODING) {
                // [language, source]
                List<String> v = values == null ? List.of() : values;
                String language = v.isEmpty() || v.get(0) == null ? "" : v.get(0).strip().toLowerCase(Locale.ROOT);
                String source = v.size() < 2 || v.get(1) == null ? "" : v.get(1);
                answers.put(id, List.of(language.length() > 20 ? language.substring(0, 20) : language,
                        source.length() > CodingSpecs.MAX_SOURCE ? source.substring(0, CodingSpecs.MAX_SOURCE) : source));
                return;
            }
            answers.put(id, values == null ? List.of() : values.stream()
                    .filter(v -> v != null).map(v -> v.length() > 500 ? v.substring(0, 500) : v).limit(10).toList());
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
                        assessmentService.options(q), q.getPoints(), q.getFigure(), assessmentService.optionFigures(q), q.getSection(),
                        q.getKind() == AssessmentQuestion.Kind.CODING ? codingSpecs.spec(q.getCodingJson()) : null))
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
                status == AssessmentInvite.Status.SUBMITTED && i.getReviewedAt() == null, i.getGrading());
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
