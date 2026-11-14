package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AiInterviewVerdict;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.dto.AiReviewDtos.AnswerMark;
import com.codewalnut.ats.dto.AiReviewDtos.Queue;
import com.codewalnut.ats.dto.AiReviewDtos.QueueRow;
import com.codewalnut.ats.dto.AiReviewDtos.ReviewPage;
import com.codewalnut.ats.dto.AiReviewDtos.VerdictBrief;
import com.codewalnut.ats.dto.AiReviewDtos.VerdictRequest;
import com.codewalnut.ats.dto.AiReviewDtos.VerdictView;
import com.codewalnut.ats.repository.AiInterviewVerdictRepository;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.MessageRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * People's verdicts on completed AI interviews (AINT-21). A reviewer watches the answers, checks the AI's
 * review, marks answers and says whether the candidate goes to the next round. The verdict is recorded
 * and posted as a team note; moving the candidate stays with the recruiter.
 */
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private static final int MAX_FEEDBACK = 4000;

    private final AssessmentInviteRepository inviteRepository;
    private final AiInterviewVerdictRepository verdictRepository;
    private final MessageRepository messageRepository;
    private final AssessmentInviteService inviteService;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /** Every submitted AI interview: the ones the signed-in person hasn't decided on first, newest first. */
    @Transactional(readOnly = true)
    public Queue queue(AppUser actor) {
        accessPolicy.require(actor, Capability.REVIEW_AI_INTERVIEWS);
        List<AssessmentInvite> done = inviteRepository.findByStatus(AssessmentInvite.Status.SUBMITTED).stream()
                .filter(i -> i.getAssessment().getCategory() == Assessment.Category.INTERVIEW)
                .toList();
        Map<UUID, List<AiInterviewVerdict>> verdicts = byInvite(done.stream().map(AssessmentInvite::getId).toList());
        String me = actor.getEmail();
        List<QueueRow> rows = done.stream().map(i -> {
            List<AiInterviewVerdict> vs = verdicts.getOrDefault(i.getId(), List.of());
            AiInterviewVerdict.Decision mine = vs.stream().filter(v -> v.getReviewerEmail().equalsIgnoreCase(me))
                    .map(AiInterviewVerdict::getDecision).findFirst().orElse(null);
            var app = i.getApplication();
            return new QueueRow(i.getId(), app.getCandidate().getName(), app.getJob().getId(), app.getJob().getTitle(), app.getStage(),
                    i.getSubmittedAt(), i.getAiScore(), i.getEvaluation(), mine, briefs(vs));
        }).sorted(Comparator.comparing((QueueRow r) -> r.mine() != null)
                .thenComparing(QueueRow::submittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new Queue(rows, (int) rows.stream().filter(r -> r.mine() == null).count());
    }

    /** One interview to review: answers with videos, the AI review and the verdicts so far. */
    @Transactional
    public ReviewPage page(AppUser actor, UUID inviteId) {
        accessPolicy.require(actor, Capability.REVIEW_AI_INTERVIEWS);
        AssessmentInvite invite = submittedInterview(inviteId);
        var app = invite.getApplication();
        return new ReviewPage(invite.getId(), app.getCandidate().getName(), app.getJob().getId(), app.getJob().getTitle(),
                app.getStage(), inviteService.detailOf(invite), views(actor, verdictRepository.findByInviteIdOrderByCreatedAtAsc(inviteId)));
    }

    /** Saves the signed-in person's verdict (saving again changes it) and tells the team. */
    @Transactional
    public ReviewPage decide(AppUser actor, UUID inviteId, VerdictRequest request) {
        accessPolicy.require(actor, Capability.REVIEW_AI_INTERVIEWS);
        AssessmentInvite invite = submittedInterview(inviteId);
        String feedback = request.feedback() == null ? null : request.feedback().strip();
        if (feedback != null && feedback.length() > MAX_FEEDBACK) {
            throw new IllegalArgumentException("feedback: keep it under " + MAX_FEEDBACK + " characters");
        }
        if (request.decision() != AiInterviewVerdict.Decision.SELECT && (feedback == null || feedback.isEmpty())) {
            throw new IllegalArgumentException("feedback: say briefly why, so the recruiter can tell the candidate's story");
        }
        List<AnswerMark> marks = request.marks() == null ? List.of() : request.marks().stream()
                .filter(m -> m.mark() != null || (m.note() != null && !m.note().isBlank()))
                .map(m -> new AnswerMark(m.position(), m.mark(), m.note() == null || m.note().isBlank() ? null : m.note().strip()))
                .toList();
        AiInterviewVerdict v = verdictRepository.findByInviteIdAndReviewerEmail(inviteId, actor.getEmail()).orElse(null);
        AiInterviewVerdict.Decision before = v == null ? null : v.getDecision();
        if (v == null) {
            v = AiInterviewVerdict.builder().inviteId(inviteId).reviewerEmail(actor.getEmail()).build();
        }
        v.setReviewerName(actor.getName());
        v.setDecision(request.decision());
        v.setFeedback(feedback == null || feedback.isEmpty() ? null : feedback);
        v.setMarksJson(marks.isEmpty() ? null : write(marks));
        verdictRepository.save(v);
        if (before != request.decision()) {
            teamNote(invite, actor, request.decision(), before, feedback);
        }
        auditService.record(actor, AuditAction.AI_INTERVIEW_VERDICT, "AssessmentInvite", inviteId,
                Map.of("decision", request.decision().name(), "changed", before != null));
        return page(actor, inviteId);
    }

    /** Verdicts per interview, for the opening's ranking. */
    public Map<UUID, List<VerdictBrief>> briefsFor(Collection<UUID> inviteIds) {
        return byInvite(inviteIds).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> briefs(e.getValue())));
    }

    // ---- helpers ----

    private AssessmentInvite submittedInterview(UUID inviteId) {
        AssessmentInvite invite = inviteService.invite(inviteId);
        if (invite.getAssessment().getCategory() != Assessment.Category.INTERVIEW) {
            throw new NotFoundException("AI interview not found");
        }
        inviteService.finalise(invite);
        if (invite.getStatus() != AssessmentInvite.Status.SUBMITTED) {
            throw new IllegalArgumentException("The candidate hasn't finished this AI interview yet");
        }
        return invite;
    }

    private Map<UUID, List<AiInterviewVerdict>> byInvite(Collection<UUID> inviteIds) {
        if (inviteIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<AiInterviewVerdict>> out = new HashMap<>();
        for (AiInterviewVerdict v : verdictRepository.findByInviteIdInOrderByCreatedAtAsc(inviteIds)) {
            out.computeIfAbsent(v.getInviteId(), k -> new java.util.ArrayList<>()).add(v);
        }
        return out;
    }

    private static List<VerdictBrief> briefs(List<AiInterviewVerdict> vs) {
        return vs.stream().map(v -> new VerdictBrief(name(v), v.getDecision())).toList();
    }

    private List<VerdictView> views(AppUser actor, List<AiInterviewVerdict> vs) {
        return vs.stream().map(v -> new VerdictView(name(v), v.getReviewerEmail(), v.getDecision(), v.getDecision().label(),
                v.getFeedback(), marks(v.getMarksJson()), v.getUpdatedAt(), v.getReviewerEmail().equalsIgnoreCase(actor.getEmail())))
                .toList();
    }

    private static String name(AiInterviewVerdict v) {
        return v.getReviewerName() == null || v.getReviewerName().isBlank() ? v.getReviewerEmail() : v.getReviewerName();
    }

    private void teamNote(AssessmentInvite invite, AppUser actor, AiInterviewVerdict.Decision decision,
            AiInterviewVerdict.Decision before, String feedback) {
        String who = actor.getName() == null || actor.getName().isBlank() ? actor.getEmail() : actor.getName();
        String icon = switch (decision) {
            case SELECT -> "✅";
            case HOLD -> "🤔";
            case REJECT -> "❌";
        };
        StringBuilder body = new StringBuilder(icon + " " + who + " reviewed the AI interview: " + decision.label());
        if (before != null) {
            body.append(" (was: ").append(before.label()).append(")");
        }
        body.append('.');
        if (feedback != null && !feedback.isEmpty()) {
            body.append("\n\n").append(feedback);
        }
        messageRepository.save(Message.builder()
                .application(invite.getApplication())
                .channel(MessageChannel.TEAM)
                .authorType(MessageAuthorType.STAFF)
                .authorEmail(actor.getEmail())
                .authorName(who)
                .body(body.toString())
                .emailed(false)
                .build());
    }

    private List<AnswerMark> marks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AnswerMark>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
