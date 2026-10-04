package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewFeedback;
import com.codewalnut.ats.domain.InterviewStatus;
import com.codewalnut.ats.dto.FeedbackDtos.Competency;
import com.codewalnut.ats.dto.FeedbackDtos.FeedbackRequest;
import com.codewalnut.ats.dto.FeedbackDtos.FeedbackSummary;
import com.codewalnut.ats.dto.FeedbackDtos.FeedbackView;
import com.codewalnut.ats.dto.FeedbackDtos.InterviewFeedbackPage;
import com.codewalnut.ats.dto.FeedbackDtos.Rating;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import com.codewalnut.ats.repository.InterviewFeedbackRepository;
import com.codewalnut.ats.repository.InterviewRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Interview feedback (INT-24…). The organiser, the listed interviewers, recruiters and admins give
 * feedback; each panel member sees the others' only after submitting their own, so opinions stay
 * independent. Hiring staff who weren't on the panel see all of it. Candidates and client contacts
 * never can (they have no staff capabilities). Feedback is a record for people to decide on; it
 * moves nothing by itself.
 */
@Service
@RequiredArgsConstructor
public class InterviewFeedbackService {

    /** The default scorecard, using the 1–4 scale from the interview questions guide. */
    public static final List<Competency> COMPETENCIES = List.of(
            new Competency("Problem solving", "Breaks the problem down, asks good questions, weighs options and edge cases."),
            new Competency("Technical depth", "Knows the role's core tools well: explains how and why, not just what."),
            new Competency("Coding / hands-on", "Writes correct, readable code and tests it without prompting."),
            new Competency("Communication", "Explains clearly, listens, adjusts to questions."),
            new Competency("Ownership and attitude", "Takes responsibility, learns from mistakes, works well with others."),
            new Competency("Role fit", "Experience and level match what the role needs."));

    /** Interviews that ended this long ago no longer appear in "waiting for your feedback". */
    static final Duration DUE_WINDOW = Duration.ofDays(30);

    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository feedbackRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AdminUpdateService adminUpdates;

    @Transactional(readOnly = true)
    public InterviewFeedbackPage page(AppUser actor, UUID interviewId) {
        Interview interview = visibleInterview(actor, interviewId);
        String me = email(actor);
        boolean onPanel = onPanel(interview, me);
        List<InterviewFeedback> all = feedbackRepository.findByInterviewIdOrderBySubmittedAtAsc(interviewId);
        InterviewFeedback mine = all.stream().filter(f -> f.getAuthorEmail().equals(me)).findFirst().orElse(null);
        List<InterviewFeedback> others = all.stream().filter(f -> !f.getAuthorEmail().equals(me)).toList();
        boolean showOthers = !onPanel || mine != null;
        return new InterviewFeedbackPage(InterviewResponse.from(interview), onPanel, canSubmit(actor, interview),
                mine == null ? null : view(mine), showOthers ? others.stream().map(this::view).toList() : List.of(),
                showOthers ? 0 : others.size(), COMPETENCIES);
    }

    @Transactional
    public InterviewFeedbackPage submit(AppUser actor, UUID interviewId, FeedbackRequest request) {
        Interview interview = visibleInterview(actor, interviewId);
        if (!canSubmit(actor, interview)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the interview's panel can give feedback");
        }
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw new IllegalArgumentException("This interview was cancelled");
        }
        if (interview.getStartAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Feedback opens when the interview starts");
        }
        // Scores count when the interview took place, even if it ended early.
        boolean held = request.attendance() == InterviewFeedback.Attendance.HELD
                || request.attendance() == InterviewFeedback.Attendance.ENDED_EARLY;
        if (held && request.recommendation() == null) {
            throw new IllegalArgumentException("recommendation: choose an overall recommendation");
        }
        List<Rating> ratings = held ? cleanRatings(request.ratings()) : List.of();
        if (held && ratings.stream().noneMatch(r -> r.rating() != null)) {
            throw new IllegalArgumentException("ratings: rate at least one competency");
        }
        String me = email(actor);
        InterviewFeedback f = feedbackRepository.findByInterviewIdAndAuthorEmail(interviewId, me).orElse(null);
        boolean created = f == null;
        if (created) {
            f = InterviewFeedback.builder().interviewId(interviewId).authorEmail(me).build();
        }
        f.setAuthorName(actor.getName());
        f.setAttendance(request.attendance());
        f.setRatingsJson(write(ratings));
        f.setStrengths(held ? blankToNull(request.strengths()) : null);
        f.setConcerns(held ? blankToNull(request.concerns()) : null);
        f.setQuestionsAsked(held ? blankToNull(request.questionsAsked()) : null);
        f.setRecommendation(held ? request.recommendation() : null);
        f.setNotes(blankToNull(request.notes()));
        feedbackRepository.save(f);
        feedbackRepository.flush();
        auditService.record(actor, created ? AuditAction.INTERVIEW_FEEDBACK_SUBMITTED : AuditAction.INTERVIEW_FEEDBACK_UPDATED,
                "Interview", interviewId, Map.of("attendance", request.attendance().name(),
                        "recommendation", request.recommendation() == null ? "" : request.recommendation().name()));
        if (created) {
            adminUpdates.feedbackSubmitted(actor, interview, view(f),
                    feedbackRepository.findByInterviewIdOrderBySubmittedAtAsc(interviewId));
        }
        return page(actor, interviewId);
    }

    /** Per interview of an application: how many have given feedback and, when you may see it, their recommendations. */
    @Transactional(readOnly = true)
    public List<FeedbackSummary> summaries(AppUser actor, UUID applicationId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        String me = email(actor);
        List<Interview> interviews = interviewRepository.findByApplicationIdOrderByStartAtDesc(applicationId);
        Map<UUID, List<InterviewFeedback>> byInterview = feedbackRepository
                .findByInterviewIdIn(interviews.stream().map(Interview::getId).toList()).stream()
                .collect(Collectors.groupingBy(InterviewFeedback::getInterviewId));
        List<FeedbackSummary> out = new ArrayList<>();
        for (Interview i : interviews) {
            List<InterviewFeedback> fs = byInterview.getOrDefault(i.getId(), List.of());
            boolean mine = fs.stream().anyMatch(f -> f.getAuthorEmail().equals(me));
            boolean visible = !onPanel(i, me) || mine;
            out.add(new FeedbackSummary(i.getId(), fs.size(), panel(i).size(),
                    visible ? fs.stream().map(InterviewFeedback::getRecommendation).filter(Objects::nonNull).toList() : List.of(),
                    mine, visible));
        }
        return out;
    }

    /** Interviews you were on that have ended (in the last 30 days) and still need your feedback. */
    @Transactional(readOnly = true)
    public List<InterviewResponse> due(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        String me = email(actor);
        Instant now = Instant.now();
        List<Interview> ended = interviewRepository.findByStatusAndEndAtAfterOrderByStartAtAsc(InterviewStatus.SCHEDULED,
                now.minus(DUE_WINDOW)).stream().filter(i -> i.getStartAt().isBefore(now) && onPanel(i, me)).toList();
        if (ended.isEmpty()) {
            return List.of();
        }
        Set<UUID> done = feedbackRepository.findByInterviewIdIn(ended.stream().map(Interview::getId).toList()).stream()
                .filter(f -> f.getAuthorEmail().equals(me)).map(InterviewFeedback::getInterviewId).collect(Collectors.toSet());
        return ended.stream().filter(i -> !done.contains(i.getId())).map(InterviewResponse::from).toList();
    }

    // ---- rules ----

    /** People who can see candidates see any interview; interviewers only those they're on. */
    private Interview visibleInterview(AppUser actor, UUID interviewId) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        Interview i = interviewRepository.findById(interviewId).orElseThrow(() -> new NotFoundException("Interview not found"));
        if (!accessPolicy.has(actor, Capability.VIEW_CANDIDATES) && !onPanel(i, email(actor))) {
            throw new NotFoundException("Interview not found");
        }
        return i;
    }

    private boolean canSubmit(AppUser actor, Interview i) {
        return onPanel(i, email(actor)) || accessPolicy.has(actor, Capability.MANAGE_JOBS);
    }

    static Set<String> panel(Interview i) {
        Set<String> p = new LinkedHashSet<>();
        p.add(i.getOrganizerEmail().toLowerCase(Locale.ROOT));
        i.interviewers().forEach(e -> p.add(e.toLowerCase(Locale.ROOT)));
        return p;
    }

    static boolean onPanel(Interview i, String email) {
        return panel(i).contains(email);
    }

    private FeedbackView view(InterviewFeedback f) {
        List<Rating> ratings = ratings(f);
        Double avg = ratings.stream().filter(r -> r.rating() != null).mapToInt(Rating::rating).average().stream().boxed()
                .map(a -> Math.round(a * 10) / 10.0).findFirst().orElse(null);
        return new FeedbackView(f.getAuthorEmail(), f.getAuthorName(), f.getAttendance(), ratings, avg, f.getStrengths(),
                f.getConcerns(), f.getQuestionsAsked(), f.getRecommendation(), f.getNotes(), f.getSubmittedAt(), f.getUpdatedAt());
    }

    private List<Rating> ratings(InterviewFeedback f) {
        if (f.getRatingsJson() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(f.getRatingsJson(), new TypeReference<List<Rating>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Rating> cleanRatings(List<Rating> given) {
        if (given == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Rating> out = new ArrayList<>();
        for (Rating r : given) {
            if (r == null || !StringUtils.hasText(r.competency()) || !seen.add(r.competency().strip().toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(new Rating(r.competency().strip(), r.rating(), blankToNull(r.note())));
        }
        return out;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String email(AppUser actor) {
        return actor.getEmail().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }
}
