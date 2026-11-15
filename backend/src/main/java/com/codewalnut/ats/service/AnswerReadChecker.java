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
 * Runs the reading check of each spoken answer as soon as it uploads (AINT-24, ADR-0031), on a small worker
 * pool, so the team hears while the interview is still running. One attempt: a check the AI couldn't do is
 * marked failed and the full review after submit still sees every answer.
 */
@Slf4j
@Service
public class AnswerReadChecker {

    private final InterviewAnswerChecks checks;
    private final InterviewAgent agent;
    private final ExecutorService executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public AnswerReadChecker(InterviewAnswerChecks checks, InterviewAgent agent, @Value("${ats.interview.async:true}") boolean async) {
        this.checks = checks;
        this.agent = agent;
        this.executor = async ? Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "answer-read-checker");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCheckRequested(AnswerCheckRequested event) {
        submit(event.checkId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePending() {
        if (executor != null) {
            checks.pendingChecks().forEach(this::submit);
        }
    }

    public void submit(UUID checkId) {
        if (executor == null) {
            check(checkId);
        } else {
            executor.submit(() -> check(checkId));
        }
    }

    /** Never throws. */
    public void check(UUID checkId) {
        if (!inFlight.add(checkId)) {
            return;
        }
        try {
            InterviewAnswerChecks.CheckWork work = checks.checkWork(checkId);
            if (work == null) {
                return;
            }
            checks.recordCheck(checkId, agent.readCheck(work.job(), work.answer()));
        } catch (CalendarException e) {
            log.warn("Reading check {} not done: {}", checkId, e.getMessage());
            checks.checkFailed(checkId);
        } catch (RuntimeException e) {
            log.error("Reading check {} failed", checkId, e);
            checks.checkFailed(checkId);
        } finally {
            inFlight.remove(checkId);
        }
    }
}
