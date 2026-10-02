package com.codewalnut.ats.service;

import com.codewalnut.ats.client.AssistantClient;
import com.codewalnut.ats.client.AssistantPlan;
import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AssistantDtos.AssistantStatus;
import com.codewalnut.ats.dto.AssistantDtos.Option;
import com.codewalnut.ats.dto.AssistantDtos.PlanResponse;
import com.codewalnut.ats.dto.AssistantDtos.ProposedAction;
import com.codewalnut.ats.dto.AssistantDtos.Unresolved;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The AI assistant on an opening: "sagar, sucheth and amogh are shortlisted". It only proposes;
 * every proposal is checked here against the opening's real candidates and stages, and nothing
 * changes until a person applies it through the normal endpoints (AGENTS.md "AI is advisory",
 * ADR-0009). Only candidate names and stages are sent to the model, never contact details.
 */
@Service
@RequiredArgsConstructor
public class AssistantService {

    static final int HOURLY_LIMIT = 60;

    private final JobOpeningRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final AssistantClient assistantClient;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public AssistantStatus status(AppUser actor) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        return new AssistantStatus(assistantClient.available());
    }

    @Transactional(readOnly = true)
    public PlanResponse plan(AppUser actor, UUID jobId, String instruction) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Opening not found"));
        throttle(actor.getEmail());
        List<Application> applications = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId);
        Map<UUID, Application> byId = new LinkedHashMap<>();
        applications.forEach(a -> byId.put(a.getId(), a));
        AssistantPlan plan = assistantClient.plan(new AssistantClient.Request(
                instruction.strip(),
                job.getTitle(),
                applications.stream()
                        .map(a -> new AssistantClient.Candidate(a.getId().toString(), a.getCandidate().getName(), a.getStage().getLabel()))
                        .toList(),
                Arrays.stream(Stage.values()).map(s -> new AssistantClient.StageOption(s.name(), s.getLabel())).toList()));

        List<String> notes = new ArrayList<>();
        List<ProposedAction> actions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AssistantPlan.Action a : nullSafe(plan.actions())) {
            Application application = byId.get(parseId(a.applicationId()));
            if (application == null) {
                notes.add("Ignored a suggestion for someone who isn't in this opening.");
                continue;
            }
            String name = application.getCandidate().getName();
            String note = StringUtils.hasText(a.note()) ? a.note().strip() : null;
            if ("MOVE_STAGE".equals(a.type())) {
                Stage to = parseStage(a.stage());
                if (to == null) {
                    notes.add("Ignored an unknown stage for " + name + ".");
                    continue;
                }
                if (to == application.getStage()) {
                    notes.add(name + " is already at " + to.getLabel() + ".");
                    continue;
                }
                if (!seen.add(application.getId() + "/move")) {
                    continue;
                }
                actions.add(new ProposedAction("MOVE_STAGE", application.getId(), name, application.getStage(),
                        application.getStage().getLabel(), to, to.getLabel(), note, to.requiresReason() && note == null));
            } else if ("ADD_NOTE".equals(a.type()) && note != null) {
                if (seen.add(application.getId() + "/note/" + note)) {
                    actions.add(new ProposedAction("ADD_NOTE", application.getId(), name, application.getStage(),
                            application.getStage().getLabel(), null, null, note, false));
                }
            }
        }
        List<Unresolved> unresolved = new ArrayList<>();
        for (AssistantPlan.Unresolved u : nullSafe(plan.unresolved())) {
            List<Option> options = nullSafe(u.possibleApplicationIds()).stream()
                    .map(this::parseId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .map(o -> new Option(o.getId(), o.getCandidate().getName(), o.getStage().getLabel()))
                    .toList();
            Stage to = parseStage(u.stage());
            unresolved.add(new Unresolved(StringUtils.hasText(u.mention()) ? u.mention().strip() : "?", options, to,
                    to != null ? to.getLabel() : null, StringUtils.hasText(u.note()) ? u.note().strip() : null));
        }
        auditService.record(actor, AuditAction.ASSISTANT_SUGGESTED, "JobOpening", jobId,
                Map.of("actions", actions.size(), "unresolved", unresolved.size()));
        return new PlanResponse(instruction.strip(), StringUtils.hasText(plan.summary()) ? plan.summary().strip() : "",
                actions, unresolved, notes, true);
    }

    /** Keeps an accidental loop (or a stuck key) from running up the bill. */
    private void throttle(String user) {
        Deque<Instant> times = recent.computeIfAbsent(user, k -> new ArrayDeque<>());
        synchronized (times) {
            Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
            while (!times.isEmpty() && times.peekFirst().isBefore(hourAgo)) {
                times.pollFirst();
            }
            if (times.size() >= HOURLY_LIMIT) {
                throw new CalendarException("You've used the assistant a lot in the last hour. Please try again later.");
            }
            times.addLast(Instant.now());
        }
    }

    private UUID parseId(String id) {
        try {
            return id == null ? null : UUID.fromString(id.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Stage parseStage(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        try {
            return Stage.valueOf(key.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
