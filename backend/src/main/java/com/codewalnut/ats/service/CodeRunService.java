package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.CodeRunner;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.dto.AssessmentDtos.CaseResult;
import com.codewalnut.ats.dto.AssessmentDtos.CodeResult;
import com.codewalnut.ats.dto.AssessmentDtos.CodingSpec;
import com.codewalnut.ats.dto.AssessmentDtos.RunCodeRequest;
import com.codewalnut.ats.dto.AssessmentDtos.RunCodeResult;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
import com.codewalnut.ats.repository.AssessmentQuestionRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Runs candidate code in the sandbox (ADR-0016): sample runs while the test is open, staff
 * checking a question, and grading submitted code against every test case. Grading happens after
 * the submit commits, on a small worker pool; if the sandbox is down it is retried (a sweep every
 * minute, CodeGradingSweeper) and picked up again after a restart.
 */
@Slf4j
@Service
public class CodeRunService {

    private static final int OUTPUT_PREVIEW = 1000;
    private static final int ERROR_PREVIEW = 2000;

    private final com.codewalnut.ats.config.OperationsMode operations;
    private final AssessmentInviteService invites;
    private final AssessmentQuestionRepository questionRepository;
    private final CodingSpecs codingSpecs;
    private final CodeRunner runner;
    private final AccessPolicy accessPolicy;
    private final ExecutorService executor;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public CodeRunService(AssessmentInviteService invites, AssessmentQuestionRepository questionRepository, CodingSpecs codingSpecs,
            CodeRunner runner, AccessPolicy accessPolicy, @Value("${ats.coding.async:true}") boolean async,
            @Value("${ats.coding.workers:2}") int workers, com.codewalnut.ats.config.OperationsMode operations) {
        this.operations = operations;
        this.invites = invites;
        this.questionRepository = questionRepository;
        this.codingSpecs = codingSpecs;
        this.runner = runner;
        this.accessPolicy = accessPolicy;
        this.executor = async ? Executors.newFixedThreadPool(Math.max(1, workers), r -> {
            Thread t = new Thread(r, "code-grader");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    public boolean available() {
        return runner.configured();
    }

    /** The candidate's Run: their code against the sample tests only. */
    public RunCodeResult runSamples(CandidateAccount account, UUID inviteId, UUID questionId, RunCodeRequest request) {
        requireRunner();
        AssessmentInviteService.RunTicket ticket = invites.startRun(account, inviteId, questionId, request.language());
        List<TestCase> samples = ticket.spec().samples();
        List<CaseResult> cases = run(ticket.spec(), request.language(), request.source(), samples, samples.size(), true);
        return result(cases, ticket.runsLeft());
    }

    /** A live coding room's Run: code against a problem's sample tests (INT-37). */
    public RunCodeResult runOnSamples(CodingSpec spec, String language, String source, int runsLeft) {
        requireRunner();
        if (!spec.languages().contains(language)) {
            throw new IllegalArgumentException("language: choose one of " + String.join(", ", spec.languages()));
        }
        List<TestCase> samples = spec.samples();
        return result(run(spec, language, source, samples, samples.size(), true), runsLeft);
    }

    /** Staff checking a question: a solution against every test, with inputs and expected outputs shown. */
    public RunCodeResult tryQuestion(AppUser actor, UUID assessmentId, UUID questionId, RunCodeRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        requireRunner();
        AssessmentQuestion q = questionRepository.findById(questionId)
                .filter(x -> x.getAssessmentId().equals(assessmentId) && x.getKind() == AssessmentQuestion.Kind.CODING)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        CodingSpec spec = codingSpecs.spec(q.getCodingJson());
        if (!spec.languages().contains(request.language())) {
            throw new IllegalArgumentException("language: choose one of " + String.join(", ", spec.languages()));
        }
        List<TestCase> all = codingSpecs.allTests(spec, q.getAnswerJson());
        return result(run(spec, request.language(), request.source(), all, spec.samples().size(), true), -1);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGradingRequested(CodeGradingRequested event) {
        submit(event.inviteId());
    }

    /** Picks up grading left unfinished by a restart. */
    @EventListener(ApplicationReadyEvent.class)
    public void resumePending() {
        if (operations.backgroundWorkEnabled() && executor != null) {
            invites.pendingGrading().forEach(this::submit);
        }
    }

    /** Grades in the background (inline when ats.coding.async is false, as in tests). */
    public void submit(UUID inviteId) {
        if (!operations.backgroundWorkEnabled()) {
            return;
        }
        if (executor == null) {
            grade(inviteId);
        } else {
            executor.submit(() -> grade(inviteId));
        }
    }

    /** Runs every coding answer of a submitted test and records the final score. Never throws. */
    public void grade(UUID inviteId) {
        if (!operations.backgroundWorkEnabled()) {
            return;
        }
        if (!inFlight.add(inviteId)) {
            return;
        }
        try {
            List<AssessmentInviteService.CodeWork> work = invites.gradingWork(inviteId);
            if (work.isEmpty()) {
                return;
            }
            if (!runner.configured()) {
                invites.gradingFailed(inviteId, "ATS_CODE_RUNNER_URL isn't set");
                return;
            }
            Map<UUID, CodeResult> results = new LinkedHashMap<>();
            for (AssessmentInviteService.CodeWork w : work) {
                if (w.source() == null || w.language() == null || !w.spec().languages().contains(w.language())) {
                    results.put(w.questionId(), new CodeResult(w.language(), w.source(), 0, w.tests().size(), 0, null, List.of()));
                    continue;
                }
                List<CaseResult> cases = run(w.spec(), w.language(), w.source(), w.tests(), w.spec().samples().size(), false);
                int passed = (int) cases.stream().filter(CaseResult::passed).count();
                String compile = cases.stream().filter(c -> "COMPILE_ERROR".equals(c.status())).map(CaseResult::error).findFirst().orElse(null);
                results.put(w.questionId(), new CodeResult(w.language(), w.source(), passed, w.tests().size(),
                        CodingSpecs.earned(w.points(), passed, w.tests().size()), compile, cases));
            }
            invites.completeGrading(inviteId, results);
        } catch (CodeRunner.UnavailableException e) {
            log.warn("Code grading for {} postponed: {}", inviteId, e.getMessage());
            invites.gradingFailed(inviteId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Code grading for {} failed", inviteId, e);
            invites.gradingFailed(inviteId, "unexpected error");
        } finally {
            inFlight.remove(inviteId);
        }
    }

    /**
     * Runs the program once per test and compares the output. Inputs and expected outputs are kept
     * for the first {@code shown} tests (the samples) when showInputs is set; hidden tests never
     * carry them here (staff views add them from the question).
     */
    private List<CaseResult> run(CodingSpec spec, String language, String source, List<TestCase> tests, int shown, boolean showInputs) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source: write some code first");
        }
        List<CodeRunner.Result> results = runner.run(language, source, tests.stream().map(TestCase::input).toList(),
                codingSpecs.limits(spec, language));
        List<CaseResult> cases = new ArrayList<>();
        for (int i = 0; i < tests.size(); i++) {
            TestCase t = tests.get(i);
            CodeRunner.Result r = i < results.size() ? results.get(i)
                    : new CodeRunner.Result(CodeRunner.Status.INTERNAL_ERROR, null, null, null, null, null);
            boolean ok = r.status() == CodeRunner.Status.OK;
            boolean passed = ok && CodingSpecs.matches(r.stdout(), t.output());
            String status = ok ? (passed ? "PASSED" : "WRONG_ANSWER") : r.status().name();
            String error = r.status() == CodeRunner.Status.COMPILE_ERROR ? r.compileOutput() : r.stderr();
            boolean sample = i < shown;
            cases.add(new CaseResult(sample, passed, status, AssessmentInviteService.clip(r.stdout(), OUTPUT_PREVIEW),
                    AssessmentInviteService.clip(error, ERROR_PREVIEW), r.timeSeconds(), r.memoryKb(),
                    showInputs ? AssessmentInviteService.clip(t.input(), 2000) : null,
                    showInputs ? AssessmentInviteService.clip(t.output(), 2000) : null));
        }
        return cases;
    }

    private static RunCodeResult result(List<CaseResult> cases, int runsLeft) {
        String compile = cases.stream().filter(c -> "COMPILE_ERROR".equals(c.status())).map(CaseResult::error).findFirst().orElse(null);
        int passed = (int) cases.stream().filter(CaseResult::passed).count();
        return new RunCodeResult(compile == null, compile, cases, passed, cases.size(), runsLeft);
    }

    private void requireRunner() {
        if (!runner.configured()) {
            throw new CalendarException("Running code isn't set up yet. An admin needs to connect the code runner (ATS_CODE_RUNNER_URL).");
        }
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
