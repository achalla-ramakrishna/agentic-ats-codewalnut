package com.codewalnut.ats.service;

import com.codewalnut.ats.client.InterviewAgent;
import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.InterviewAnswerCheck;
import com.codewalnut.ats.domain.InterviewAnswerCheck.Reading;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.dto.AiInterviewDtos.RevealedQuestion;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.AssessmentQuestionRepository;
import com.codewalnut.ats.repository.InterviewAnswerCheckRepository;
import com.codewalnut.ats.repository.MessageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading checks for AI interviews (AINT-24, ADR-0031). A spoken question is hidden until the candidate
 * opens it; they get a short time to think and recording starts; each answer may be recorded a limited
 * number of times. As soon as an answer uploads, the interview agent checks whether it sounds read out,
 * and the team is told while the interview is still running. All of it is advisory: a person watches the
 * video and decides, and only a person can end an interview early.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnswerChecks {

    /** Shorter answers say too little to judge. */
    static final int MIN_WORDS = 25;
    static final String WARNING = "Some of your answers sound as if they were read out. Please answer in your own words, as you "
            + "would to an interviewer in the room — the CodeWalnut team watches every video.";

    private final InterviewAnswerCheckRepository checkRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final MessageRepository messageRepository;
    private final com.codewalnut.ats.repository.InterviewVideoRepository videoRepository;
    private final AssessmentInviteService inviteService;
    private final InterviewAgent agent;
    private final InterviewRules rules;
    private final ApplicationEventPublisher events;

    // ---- the candidate ----

    /** Shows a spoken question (the first time starts its thinking time); the same answer when asked again. */
    @Transactional
    public RevealedQuestion reveal(CandidateAccount account, UUID inviteId, UUID questionId) {
        AssessmentInvite invite = inviteService.ownInvite(account, inviteId);
        if (invite.getAssessment().getCategory() != Assessment.Category.INTERVIEW
                || inviteService.finalise(invite) != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException("This interview isn't open any more.");
        }
        AssessmentQuestion q = spoken(invite, questionId);
        InterviewAnswerCheck check = checkRepository.findByInviteIdAndQuestionId(inviteId, questionId)
                .orElseGet(() -> checkRepository.save(InterviewAnswerCheck.builder()
                        .inviteId(inviteId).questionId(questionId).revealedAt(Instant.now()).build()));
        return revealed(q, check, warned(inviteId));
    }

    RevealedQuestion revealed(AssessmentQuestion q, InterviewAnswerCheck check, boolean warn) {
        long since = Duration.between(check.getRevealedAt(), Instant.now()).getSeconds();
        int thinkLeft = check.getTakes() > 0 ? 0 : (int) Math.max(0, rules.thinkSeconds() - since);
        return new RevealedQuestion(q.getId(), q.getPrompt(), q.getCode(), check.getRevealedAt(), rules.thinkSeconds(), thinkLeft,
                Math.max(0, rules.takes() - check.getTakes()), warn ? WARNING : null);
    }

    /** True once any of this interview's answers sounded read out, so the candidate is warned before the next one. */
    boolean warned(UUID inviteId) {
        return checkRepository.findByInviteId(inviteId).stream().anyMatch(c -> c.getReading() == Reading.LIKELY);
    }

    /**
     * Before a recording is saved: the question must have been opened, and takes must be left. Answers recorded
     * before reading checks existed count as opened, with one take.
     */
    InterviewAnswerCheck beforeUpload(AssessmentInvite invite, UUID questionId, boolean hadRecording) {
        InterviewAnswerCheck check = checkRepository.findByInviteIdAndQuestionId(invite.getId(), questionId).orElse(null);
        if (check == null) {
            if (!hadRecording) {
                throw new IllegalArgumentException("Open the question before recording your answer.");
            }
            check = InterviewAnswerCheck.builder().inviteId(invite.getId()).questionId(questionId).revealedAt(Instant.now()).takes(1).build();
        }
        if (check.getTakes() >= rules.takes()) {
            throw new IllegalArgumentException(rules.takes() == 1
                    ? "This answer is already recorded; each question is answered once."
                    : "You've already recorded this answer " + rules.takes() + " times, the most allowed.");
        }
        return check;
    }

    /** After a recording is saved: count the take, note the signals, and queue the reading check. */
    void afterUpload(InterviewAnswerCheck check, int seconds, int focusLosses, String transcript) {
        check.setTakes(check.getTakes() + 1);
        if (check.getTakes() == 1 && check.getThinkSeconds() == null) {
            long since = Duration.between(check.getRevealedAt(), Instant.now()).getSeconds();
            check.setThinkSeconds((int) Math.max(0, since - seconds));
        }
        check.setFocusLosses(check.getFocusLosses() + Math.max(0, Math.min(focusLosses, 100)));
        boolean judge = agent.usesAi() && words(transcript) >= MIN_WORDS;
        check.setReading(judge ? Reading.PENDING : Reading.SKIPPED);
        check.setReadingReason(null);
        check.setCheckedAt(null);
        check = checkRepository.save(check);
        if (judge) {
            events.publishEvent(new AnswerCheckRequested(check.getId()));
        }
    }

    static int words(String text) {
        return text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }

    // ---- the check, run in the background by AnswerReadChecker ----

    public record CheckWork(InterviewAgent.Job job, InterviewAgent.ReadCheckInput answer) {}

    /** What the agent needs, or null when this answer isn't waiting for a check. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CheckWork checkWork(UUID checkId) {
        InterviewAnswerCheck check = checkRepository.findById(checkId).orElse(null);
        if (check == null || check.getReading() != Reading.PENDING) {
            return null;
        }
        AssessmentInvite invite = inviteRepository.findById(check.getInviteId()).orElse(null);
        AssessmentQuestion q = questionRepository.findById(check.getQuestionId()).orElse(null);
        if (invite == null || q == null) {
            return null;
        }
        List<String> given = inviteService.answers(invite).getOrDefault(q.getId(), List.of());
        String transcript = given.size() > 1 ? given.get(1) : "";
        int seconds = videoRepository.findByInviteIdAndQuestionId(invite.getId(), q.getId())
                .map(com.codewalnut.ats.domain.InterviewVideo::getDurationSeconds).orElse(0);
        JobOpening job = invite.getApplication().getJob();
        String asked = q.getCode() == null ? q.getPrompt() : q.getPrompt() + "\nCode shown:\n" + q.getCode();
        return new CheckWork(new InterviewAgent.Job(job.getTitle(), job.getDescription()),
                new InterviewAgent.ReadCheckInput(asked, transcript, seconds));
    }

    /** Records the agent's verdict and, once enough answers sound read out, tells the team (once per interview). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCheck(UUID checkId, InterviewAgent.ReadCheck result) {
        InterviewAnswerCheck check = checkRepository.findById(checkId).orElse(null);
        if (check == null || check.getReading() != Reading.PENDING) {
            return;
        }
        Reading reading = result == null ? Reading.SKIPPED : parse(result.verdict());
        check.setReading(reading);
        String reason = result == null || result.reason() == null ? null : result.reason().strip();
        check.setReadingReason(reason == null || reason.isEmpty() ? null : reason.length() > 600 ? reason.substring(0, 600) : reason);
        check.setCheckedAt(Instant.now());
        if (reading == Reading.LIKELY) {
            alert(check);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkFailed(UUID checkId) {
        checkRepository.findById(checkId).filter(c -> c.getReading() == Reading.PENDING).ifPresent(c -> {
            c.setReading(Reading.FAILED);
            c.setCheckedAt(Instant.now());
        });
    }

    @Transactional(readOnly = true)
    public List<UUID> pendingChecks() {
        return checkRepository.findByReading(Reading.PENDING).stream().map(InterviewAnswerCheck::getId).toList();
    }

    private void alert(InterviewAnswerCheck check) {
        AssessmentInvite invite = inviteRepository.findById(check.getInviteId()).orElse(null);
        if (invite == null || invite.getReadingAlertedAt() != null) {
            return;
        }
        List<InterviewAnswerCheck> all = checkRepository.findByInviteId(invite.getId());
        long likely = all.stream().filter(c -> c.getReading() == Reading.LIKELY || c.getId().equals(check.getId())).count();
        if (likely < rules.readingAlertAfter()) {
            return;
        }
        invite.setReadingAlertedAt(Instant.now());
        AssessmentQuestion q = questionRepository.findById(check.getQuestionId()).orElse(null);
        boolean running = invite.getStatus() == AssessmentInvite.Status.STARTED;
        String name = invite.getApplication().getCandidate().getName();
        messageRepository.save(Message.builder()
                .application(invite.getApplication())
                .channel(MessageChannel.TEAM)
                .authorType(MessageAuthorType.STAFF)
                .authorEmail(AssessmentInviteService.SYSTEM_AUTHOR)
                .authorName("CodeWalnut ATS")
                .body("⚠ " + name + " may be reading their AI interview answers"
                        + (q == null ? "" : " (question " + q.getPosition() + ")") + ": "
                        + (check.getReadingReason() == null ? "the answer sounds read out." : check.getReadingReason())
                        + (running ? " The interview is still running: watch the answer on the opening's AI interview tab and click "
                                + "End interview now if you agree." : " Watch the flagged answers before deciding.")
                        + " Advisory — the AI can be wrong.")
                .emailed(false)
                .build());
        log.info("Reading alert for interview {} ({} answer(s) flagged)", invite.getId(), likely);
    }

    private static Reading parse(String verdict) {
        if (verdict == null) {
            return Reading.SKIPPED;
        }
        return switch (verdict.strip().toUpperCase(java.util.Locale.ROOT)) {
            case "LIKELY" -> Reading.LIKELY;
            case "POSSIBLE" -> Reading.POSSIBLE;
            case "UNLIKELY" -> Reading.UNLIKELY;
            default -> Reading.SKIPPED;
        };
    }

    // ---- staff ----

    /** Every check of these interviews, by invite and question. */
    @Transactional(readOnly = true)
    public Map<UUID, Map<UUID, InterviewAnswerCheck>> forInvites(java.util.Collection<UUID> inviteIds) {
        if (inviteIds.isEmpty()) {
            return Map.of();
        }
        return checkRepository.findByInviteIdIn(inviteIds).stream().collect(java.util.stream.Collectors.groupingBy(
                InterviewAnswerCheck::getInviteId, java.util.stream.Collectors.toMap(InterviewAnswerCheck::getQuestionId, c -> c, (a, b) -> a)));
    }

    private AssessmentQuestion spoken(AssessmentInvite invite, UUID questionId) {
        return questionRepository.findById(questionId)
                .filter(x -> x.getAssessmentId().equals(invite.getAssessment().getId()) && x.getKind() == AssessmentQuestion.Kind.VIDEO)
                .orElseThrow(() -> new NotFoundException("Question not found"));
    }
}
