package com.codewalnut.ats.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs résumé reading in the background, a few at a time, so a 40-résumé upload returns at once
 * and the AI provider isn't flooded. Tests set {@code ats.ai.async=false} to run work inline.
 * Work left unfinished by a restart is picked up again at start-up (ResumeIntelligenceService).
 */
@Slf4j
@Component
public class AiWorkQueue {

    private final com.codewalnut.ats.config.OperationsMode operations;
    private final boolean async;
    private final ExecutorService executor;

    public AiWorkQueue(@Value("${ats.ai.async:true}") boolean async, @Value("${ats.ai.workers:3}") int workers,
            com.codewalnut.ats.config.OperationsMode operations) {
        this.operations = operations;
        this.async = async;
        AtomicInteger n = new AtomicInteger();
        this.executor = async
                ? Executors.newFixedThreadPool(Math.max(1, workers), r -> {
                    Thread t = new Thread(r, "ai-worker-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                })
                : null;
    }

    /** Runs the task once the current transaction commits (so the rows it reads exist), or now if there is none. */
    public void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(task);
                }
            });
        } else {
            submit(task);
        }
    }

    /** Runs the task in the background (inline when async is off). Never throws. */
    public void submit(Runnable task) {
        if (!operations.backgroundWorkEnabled()) {
            return;
        }
        Runnable safe = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Background AI work failed", e);
            }
        };
        if (executor == null) {
            safe.run();
        } else {
            executor.execute(safe);
        }
    }

    public boolean isAsync() {
        return async;
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
