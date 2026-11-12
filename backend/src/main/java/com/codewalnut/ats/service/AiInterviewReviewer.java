package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.InterviewAgent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the interview agent's review after an AI interview is submitted (ADR-0030), on a small
 * worker pool; reviews the AI couldn't do are retried every few minutes and after a restart.
 */
@Slf4j
@Service
public class AiInterviewReviewer {

    private final AiInterviewService interviews;
    private final InterviewAgent agent;
    private final ExecutorService executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public AiInterviewReviewer(AiInterviewService interviews, InterviewAgent agent, @Value("${ats.interview.async:true}") boolean async) {
        this.interviews = interviews;
        this.agent = agent;
        this.executor = async ? Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "interview-reviewer");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewRequested(InterviewReviewRequested event) {
        submit(event.inviteId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePending() {
        if (executor != null) {
            interviews.pendingReviews().forEach(this::submit);
        }
    }

    public void submit(UUID inviteId) {
        if (executor == null) {
            review(inviteId);
        } else {
            executor.submit(() -> review(inviteId));
        }
    }

    /** Never throws. */
    public void review(UUID inviteId) {
        if (!inFlight.add(inviteId)) {
            return;
        }
        try {
            AiInterviewService.ReviewWork work = interviews.reviewWork(inviteId);
            if (work == null) {
                return;
            }
            InterviewAgent.Review review = agent.review(work.job(), work.answers());
            interviews.completeReview(inviteId, agent.usesAi(), agent.model(), review);
        } catch (CalendarException e) {
            log.warn("Interview review for {} postponed: {}", inviteId, e.getMessage());
            interviews.reviewFailed(inviteId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Interview review for {} failed", inviteId, e);
            interviews.reviewFailed(inviteId, "unexpected error");
        } finally {
            inFlight.remove(inviteId);
        }
    }
}
