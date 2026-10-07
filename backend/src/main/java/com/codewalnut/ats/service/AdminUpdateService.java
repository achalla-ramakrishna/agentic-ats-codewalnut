package com.codewalnut.ats.service;

import com.codewalnut.ats.client.MailClient;
import com.codewalnut.ats.domain.AdminUpdate;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewFeedback;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.FeedbackDtos.FeedbackView;
import com.codewalnut.ats.dto.FeedbackDtos.Rating;
import com.codewalnut.ats.repository.AdminUpdateRepository;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.InterviewFeedbackRepository;
import com.codewalnut.ats.repository.InterviewRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Admin updates (ADM-14…, ADR-0018): when someone first submits interview feedback, or moves a
 * candidate to one of a few important stages, admins get a short candidate summary. It is always
 * kept in the app (Admin updates page) and, after the change is saved, emailed to the other admins
 * from the acting person's Gmail when they have connected it (the app has no mailbox of its own,
 * ADR-0006). Emailing is best effort and never undoes or blocks the change. Staff only: nothing
 * here reaches candidates or clients.
 */
@Service
public class AdminUpdateService {

    /** Raised after the update row is saved; emailing happens after commit, on the request thread. */
    public record Created(UUID updateId) {}

    private static final Logger log = LoggerFactory.getLogger(AdminUpdateService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a z", Locale.ENGLISH);
    private static final Map<InterviewFeedback.Recommendation, String> RECOMMENDATION = Map.of(
            InterviewFeedback.Recommendation.STRONG_YES, "Strong hire",
            InterviewFeedback.Recommendation.YES, "Hire",
            InterviewFeedback.Recommendation.NO, "No hire",
            InterviewFeedback.Recommendation.STRONG_NO, "Strong no hire");
    private static final Map<InterviewFeedback.Attendance, String> ATTENDANCE = Map.of(
            InterviewFeedback.Attendance.HELD, "The interview happened",
            InterviewFeedback.Attendance.ENDED_EARLY, "The interview ended early",
            InterviewFeedback.Attendance.CANDIDATE_NO_SHOW, "The candidate didn't join",
            InterviewFeedback.Attendance.INTERVIEWER_COULD_NOT_JOIN, "The interviewer couldn't join");

    private final AdminUpdateRepository updateRepository;
    private final AppUserRepository userRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository feedbackRepository;
    private final AccessPolicy accessPolicy;
    private final MailClient mailClient;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate newTransaction;
    private final Set<Stage> keyStages;

    public AdminUpdateService(AdminUpdateRepository updateRepository, AppUserRepository userRepository,
            AssessmentInviteRepository inviteRepository, InterviewRepository interviewRepository,
            InterviewFeedbackRepository feedbackRepository, AccessPolicy accessPolicy, MailClient mailClient,
            ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
            @Value("${ats.admin-updates.stages:SHORTLISTED,OFFER_SENT,JOINED}") Set<Stage> keyStages) {
        this.updateRepository = updateRepository;
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.interviewRepository = interviewRepository;
        this.feedbackRepository = feedbackRepository;
        this.accessPolicy = accessPolicy;
        this.mailClient = mailClient;
        this.events = events;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.keyStages = keyStages.isEmpty() ? EnumSet.noneOf(Stage.class) : EnumSet.copyOf(keyStages);
    }

    /** The stages that send an update; the rest are routine. */
    public Set<Stage> keyStages() {
        return keyStages.isEmpty() ? EnumSet.noneOf(Stage.class) : EnumSet.copyOf(keyStages);
    }

    @Transactional(readOnly = true)
    public List<AdminUpdate> latest(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_ADMIN_UPDATES);
        return updateRepository.findTop100ByOrderByCreatedAtDesc();
    }

    /** First submission of a panel member's feedback. Joins the caller's transaction. */
    public void feedbackSubmitted(AppUser actor, Interview interview, FeedbackView feedback, List<InterviewFeedback> all) {
        Application a = interview.getApplication();
        String who = StringUtils.hasText(feedback.authorName()) ? feedback.authorName() : feedback.authorEmail();
        String verdict = feedback.recommendation() != null ? RECOMMENDATION.get(feedback.recommendation())
                : ATTENDANCE.get(feedback.attendance());
        String title = a.getCandidate().getName() + ": " + verdict + " from " + who + " (" + a.getJob().getTitle() + ")";

        StringBuilder b = new StringBuilder();
        candidate(b, a);
        b.append("\nInterview: ").append(interview.getTitle()).append(", ").append(when(interview)).append('\n');
        b.append("Feedback from ").append(who).append(" (").append(feedback.authorEmail()).append("):\n");
        b.append("  ").append(ATTENDANCE.get(feedback.attendance()));
        if (feedback.recommendation() != null) {
            b.append(" · Recommendation: ").append(RECOMMENDATION.get(feedback.recommendation()));
        }
        if (feedback.averageRating() != null) {
            b.append(" · Average ").append(feedback.averageRating()).append(" / 4");
        }
        b.append('\n');
        List<String> scored = new ArrayList<>();
        for (Rating r : feedback.ratings()) {
            if (r.rating() != null) {
                scored.add(r.competency() + " " + r.rating());
            }
        }
        if (!scored.isEmpty()) {
            b.append("  Ratings: ").append(String.join(", ", scored)).append('\n');
        }
        line(b, "  Strengths: ", feedback.strengths());
        line(b, "  Concerns: ", feedback.concerns());
        line(b, "  Notes: ", feedback.notes());
        int panel = InterviewFeedbackService.panel(interview).size();
        b.append("Panel so far: ").append(all.size()).append(" of ").append(panel).append(" have given feedback");
        String recs = recommendations(all);
        b.append(recs.isEmpty() ? "" : " (" + recs + ")").append('\n');
        save(actor, AdminUpdate.Kind.FEEDBACK_SUBMITTED, a, interview.getId(), title, b.toString());
    }

    /** A move into one of the key stages. Joins the caller's transaction. */
    public void stageChanged(AppUser actor, Application a, Stage from, Stage to, String note) {
        if (!keyStages.contains(to)) {
            return;
        }
        String title = a.getCandidate().getName() + ": " + to.getLabel() + " (" + a.getJob().getTitle() + ")";
        StringBuilder b = new StringBuilder();
        b.append(name(actor)).append(" moved ").append(a.getCandidate().getName()).append(" from ")
                .append(from.getLabel()).append(" to ").append(to.getLabel()).append(".\n");
        line(b, "Note: ", note);
        b.append('\n');
        candidate(b, a);
        List<Interview> interviews = interviewRepository.findByApplicationIdOrderByStartAtDesc(a.getId());
        if (!interviews.isEmpty()) {
            List<InterviewFeedback> fs = feedbackRepository.findByInterviewIdIn(interviews.stream().map(Interview::getId).toList());
            String recs = recommendations(fs);
            b.append("Interviews: ").append(interviews.size()).append(", feedback from ").append(fs.size())
                    .append(recs.isEmpty() ? "" : " (" + recs + ")").append('\n');
        }
        save(actor, AdminUpdate.Kind.STAGE_REACHED, a, null, title, b.toString());
    }

    /** After the change commits: email the other active admins from the actor's Gmail, if connected. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(Created event) {
        AdminUpdate u = updateRepository.findById(event.updateId()).orElse(null);
        if (u == null) {
            return;
        }
        List<String> to = userRepository.findAllByOrderByEmailAsc().stream()
                .filter(x -> x.isActive() && x.rolesView().contains(Role.ADMIN))
                .map(AppUser::getEmail)
                .filter(e -> !e.equalsIgnoreCase(u.getActorEmail()))
                .toList();
        AdminUpdate.EmailStatus status;
        List<String> sent = new ArrayList<>();
        if (to.isEmpty()) {
            status = AdminUpdate.EmailStatus.NONE;
        } else if (!mailClient.status().connected()) {
            status = AdminUpdate.EmailStatus.SKIPPED;
        } else {
            String body = u.getBody() + link(u) + "\nYou get this because you're an admin of CodeWalnut ATS.\n";
            for (String address : to) {
                try {
                    mailClient.send(new MailClient.Email(address, "[CodeWalnut ATS] " + u.getTitle(), body));
                    sent.add(address);
                } catch (RuntimeException e) {
                    log.warn("Admin update {} not emailed to an admin: {}", u.getId(), e.getMessage());
                }
            }
            status = sent.size() == to.size() ? AdminUpdate.EmailStatus.SENT : AdminUpdate.EmailStatus.FAILED;
        }
        AdminUpdate.EmailStatus finalStatus = status;
        newTransaction.executeWithoutResult(tx -> updateRepository.findById(u.getId()).ifPresent(x -> {
            x.setEmailStatus(finalStatus);
            x.setEmailedTo(sent.isEmpty() ? null : String.join(", ", sent));
            updateRepository.save(x);
        }));
    }

    // ---- helpers ----

    private void save(AppUser actor, AdminUpdate.Kind kind, Application a, UUID interviewId, String title, String body) {
        AdminUpdate u = updateRepository.save(AdminUpdate.builder()
                .kind(kind)
                .applicationId(a.getId())
                .interviewId(interviewId)
                .title(title.length() > 300 ? title.substring(0, 299) + "…" : title)
                .body(body)
                .actorEmail(actor.getEmail().toLowerCase(Locale.ROOT))
                .build());
        events.publishEvent(new Created(u.getId()));
    }

    private void candidate(StringBuilder b, Application a) {
        Candidate c = a.getCandidate();
        b.append("Candidate: ").append(c.getName()).append('\n');
        b.append("Opening: ").append(a.getJob().getTitle());
        if (a.getJob().getClient() != null) {
            b.append(" (client: ").append(a.getJob().getClient().getName()).append(')');
        }
        b.append('\n');
        b.append("Stage: ").append(a.getStage().getLabel()).append('\n');
        line(b, "Email: ", c.getEmail());
        line(b, "Phone: ", c.getPhone());
        List<String> edu = new ArrayList<>();
        if (StringUtils.hasText(c.getDegree())) {
            edu.add(c.getDegree());
        }
        if (StringUtils.hasText(c.getCollege())) {
            edu.add(c.getCollege());
        }
        if (c.getGraduationYear() != null) {
            edu.add(String.valueOf(c.getGraduationYear()));
        }
        if (!edu.isEmpty()) {
            b.append("Education: ").append(String.join(", ", edu)).append('\n');
        }
        List<String> tests = inviteRepository.findByApplicationIdOrderBySentAtDesc(a.getId()).stream()
                .filter(i -> i.getStatus() == AssessmentInvite.Status.SUBMITTED && i.getPercent() != null)
                .limit(3)
                .map(i -> i.getAssessment().getTitle() + " " + i.getPercent() + "%"
                        + (i.getPercent() >= i.getAssessment().getPassPercent() ? " (passed)" : " (below pass mark)"))
                .toList();
        if (!tests.isEmpty()) {
            b.append("Tests: ").append(String.join("; ", tests)).append('\n');
        }
    }

    private static String recommendations(List<InterviewFeedback> fs) {
        return fs.stream().map(InterviewFeedback::getRecommendation).filter(java.util.Objects::nonNull)
                .map(RECOMMENDATION::get).collect(Collectors.joining(", "));
    }

    private static String when(Interview i) {
        ZoneId zone;
        try {
            zone = ZoneId.of(i.getTimeZone());
        } catch (RuntimeException e) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        return WHEN.format(i.getStartAt().atZone(zone));
    }

    private static String link(AdminUpdate u) {
        try {
            String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            return "\nOpen in the ATS: " + base + "/admin/updates\n";
        } catch (IllegalStateException e) {
            return "";
        }
    }

    private static String name(AppUser actor) {
        return StringUtils.hasText(actor.getName()) ? actor.getName() : actor.getEmail();
    }

    private static void line(StringBuilder b, String label, String value) {
        if (StringUtils.hasText(value)) {
            b.append(label).append(value.strip()).append('\n');
        }
    }
}
