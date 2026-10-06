package com.codewalnut.ats.service;

import com.codewalnut.ats.client.AskClient;
import com.codewalnut.ats.client.AskClient.ToolSpec;
import com.codewalnut.ats.client.AskClient.Turn;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AskConversation;
import com.codewalnut.ats.domain.JobStatus;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AskDtos.AskStatus;
import com.codewalnut.ats.dto.AskDtos.ChatMessage;
import com.codewalnut.ats.dto.AskDtos.Conversation;
import com.codewalnut.ats.dto.AskDtos.ConversationSummary;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import com.codewalnut.ats.dto.TrackerDtos.ApplicationResponse;
import com.codewalnut.ats.dto.TrackerDtos.EventResponse;
import com.codewalnut.ats.dto.TrackerDtos.JobResponse;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.AskConversationRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Ask ATS (ASK-01…ASK-05, ADR-0021): a chat where staff ask about openings, candidates, interviews,
 * feedback and tests. The AI looks things up through read-only tools that call the same services
 * as the screens, as the person asking, so an answer never shows more than they could see. Chats
 * are private to their owner.
 */
@Service
@RequiredArgsConstructor
public class AskService {

    static final int MAX_TURNS_SENT = 20;
    static final int MAX_LIST = 100;

    static final List<String> SUGGESTIONS = List.of(
            "Which openings are open, and how many candidates are in each?",
            "Who is shortlisted right now?",
            "What interviews are coming up this week?",
            "Whose interview feedback is still pending?",
            "Any new test results?",
            "How do I share a candidate with a client?");

    private final AskClient askClient;
    private final AskConversationRepository conversations;
    private final ApplicationRepository applicationRepository;
    private final TrackerService tracker;
    private final InterviewService interviews;
    private final InterviewFeedbackService feedback;
    private final AssessmentInviteService tests;
    private final ResumeIntelligenceService insights;
    private final AccessPolicy accessPolicy;
    private final ObjectMapper objectMapper;
    private final String guide = resource("assistant/ats-guide.md");

    // ---- chats ----

    public AskStatus status(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_DASHBOARD);
        return new AskStatus(askClient.available(), SUGGESTIONS);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_DASHBOARD);
        return conversations.findByOwnerEmailOrderByUpdatedAtDesc(actor.getEmail(), PageRequest.of(0, 50)).stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Conversation get(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.VIEW_DASHBOARD);
        return view(own(actor, id));
    }

    @Transactional
    public void delete(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.VIEW_DASHBOARD);
        conversations.delete(own(actor, id));
    }

    /** Answers the question (in a new chat, or continuing one) and saves both. */
    @Transactional
    public Conversation ask(AppUser actor, UUID conversationId, String question) {
        accessPolicy.require(actor, Capability.VIEW_DASHBOARD);
        String q = question.strip();
        AskConversation chat = conversationId == null ? null : own(actor, conversationId);
        List<ChatMessage> messages = chat == null ? new ArrayList<>() : new ArrayList<>(read(chat));
        List<Turn> history = new ArrayList<>();
        messages.stream().skip(Math.max(0, messages.size() - MAX_TURNS_SENT))
                .forEach(m -> history.add(new Turn("user".equals(m.role()), m.text())));
        history.add(new Turn(true, q));

        AskClient.Answer answer = askClient.answer(history, guide, TOOLS, (name, input) -> tool(actor, name, input));

        Instant now = Instant.now();
        messages.add(new ChatMessage("user", q, now));
        messages.add(new ChatMessage("assistant", answer.text(), Instant.now()));
        if (chat == null) {
            chat = AskConversation.builder().ownerEmail(actor.getEmail()).title(title(q)).createdAt(now).build();
        }
        chat.setMessagesJson(write(messages));
        chat.setUpdatedAt(Instant.now());
        return view(conversations.save(chat));
    }

    // ---- tools ----

    private static Map<String, Object> str(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> oneOf(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }

    static final List<ToolSpec> TOOLS = List.of(
            new ToolSpec("list_openings",
                    "Openings (jobs) with their client, status, number of positions and how many candidates are at each stage.",
                    Map.of("status", oneOf("Which openings; default OPEN (open and on hold are both 'active').",
                            List.of("OPEN", "ON_HOLD", "CLOSED", "ALL"))),
                    List.of()),
            new ToolSpec("search_candidates",
                    "Candidates (one row per candidate per opening) with opening, client, stage and last update. "
                            + "Filter by part of a name, email or phone, by stage, and/or by opening. Leave query empty to list. "
                            + "Contact details are never returned: people open the candidate in the app for those.",
                    Map.of("query", str("Part of a name, email or phone; empty for all"),
                            "stage", oneOf("Only candidates at this stage", Arrays.stream(Stage.values()).map(Enum::name).toList()),
                            "jobId", str("Only this opening (id from list_openings)")),
                    List.of()),
            new ToolSpec("get_candidate",
                    "Everything about one candidate in one opening: details, stage history and notes, other openings, "
                            + "interviews with feedback status, test results and the AI reading of their résumé (skills, "
                            + "experience, fit to the opening).",
                    Map.of("applicationId", str("The applicationId from search_candidates")),
                    List.of("applicationId")),
            new ToolSpec("opening_profiles",
                    "For one opening: each candidate's résumé profile (fit %, headline, skills, experience in months, "
                            + "graduation year). Use it for skill or experience questions across an opening.",
                    Map.of("jobId", str("The opening's id")),
                    List.of("jobId")),
            new ToolSpec("list_interviews",
                    "Interviews: upcoming (scheduled from now on), recent (started in the last 30 days, with how many of "
                            + "the panel have given feedback), or feedback_due (the asker's own interviews still waiting for their feedback).",
                    Map.of("when", oneOf("Which interviews", List.of("upcoming", "recent", "feedback_due"))),
                    List.of("when")),
            new ToolSpec("test_results",
                    "Online test results. With jobId: every test sent for that opening (status, score, pass). "
                            + "Without: submitted results nobody has looked at yet.",
                    Map.of("jobId", str("The opening's id (optional)")),
                    List.of()),
            new ToolSpec("recent_activity",
                    "The latest changes across all openings: stage moves, notes, candidates added (newest first, up to 25).",
                    Map.of(),
                    List.of()));

    String tool(AppUser actor, String name, Map<String, Object> input) {
        return switch (name) {
            case "list_openings" -> listOpenings(actor, text(input, "status"));
            case "search_candidates" -> searchCandidates(actor, text(input, "query"), text(input, "stage"), text(input, "jobId"));
            case "get_candidate" -> getCandidate(actor, uuid(input, "applicationId"));
            case "opening_profiles" -> openingProfiles(actor, uuid(input, "jobId"));
            case "list_interviews" -> listInterviews(actor, Objects.requireNonNullElse(text(input, "when"), "upcoming"));
            case "test_results" -> testResults(actor, text(input, "jobId"));
            case "recent_activity" -> recentActivity(actor);
            default -> throw new IllegalArgumentException("Unknown lookup " + name);
        };
    }

    private String listOpenings(AppUser actor, String status) {
        allow(actor, Capability.VIEW_JOBS, "openings");
        String s = status == null ? "OPEN" : status.toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rows = tracker.listJobs(actor).stream()
                .filter(j -> switch (s) {
                    case "ALL" -> true;
                    case "OPEN" -> j.status() != JobStatus.CLOSED;
                    default -> j.status().name().equals(s);
                })
                .map(AskService::opening)
                .toList();
        return json(Map.of("count", rows.size(), "openings", rows));
    }

    private static Map<String, Object> opening(JobResponse j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", j.id());
        m.put("title", j.title());
        m.put("client", j.client() == null ? "CodeWalnut (internal)" : j.client().name());
        m.put("status", j.status());
        m.put("positions", j.openings());
        m.put("location", j.location());
        m.put("candidates", j.total());
        Map<String, Long> stages = new LinkedHashMap<>();
        j.stageCounts().forEach((stage, n) -> {
            if (n > 0) {
                stages.put(stage.getLabel(), n);
            }
        });
        m.put("byStage", stages);
        return m;
    }

    private String searchCandidates(AppUser actor, String query, String stage, String jobId) {
        allow(actor, Capability.VIEW_CANDIDATES, "candidates");
        Stage st = stage == null ? null : Stage.valueOf(stage.toUpperCase(Locale.ROOT));
        List<ApplicationResponse> rows;
        if (jobId != null) {
            String q = query == null ? null : query.toLowerCase(Locale.ROOT);
            rows = tracker.listApplications(actor, UUID.fromString(jobId)).stream()
                    .filter(a -> st == null || a.stage() == st)
                    .filter(a -> q == null || contains(a.name(), q) || contains(a.email(), q) || contains(a.phone(), q))
                    .toList();
        } else {
            rows = tracker.searchApplications(actor, query, st);
        }
        List<Map<String, Object>> out = rows.stream().limit(MAX_LIST).map(AskService::candidate).toList();
        return json(Map.of("count", rows.size(), "shown", out.size(), "candidates", out));
    }

    private static Map<String, Object> candidate(ApplicationResponse a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", a.id());
        m.put("jobId", a.jobId());
        m.put("name", a.name());
        m.put("opening", a.jobTitle());
        m.put("client", a.clientName());
        m.put("stage", a.stageLabel());
        m.put("updatedAt", a.updatedAt());
        if (a.lastNote() != null) {
            m.put("lastNote", a.lastNote());
        }
        return m;
    }

    private String getCandidate(AppUser actor, UUID applicationId) {
        allow(actor, Capability.VIEW_CANDIDATES, "candidates");
        Application a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("No candidate with that applicationId"));
        Map<String, Object> m = new LinkedHashMap<>(candidate(ApplicationResponse.from(a, null)));
        m.put("source", a.getSource());
        m.put("history", tracker.history(actor, applicationId).stream().limit(20).map(AskService::event).toList());
        m.put("otherOpenings", tracker.openings(actor, applicationId));
        if (accessPolicy.has(actor, Capability.VIEW_INTERVIEWS)) {
            Map<UUID, Object> summaries = new LinkedHashMap<>();
            feedback.summaries(actor, applicationId).forEach(s -> summaries.put(s.interviewId(), s.visible()
                    ? Map.of("feedbackGiven", s.submitted(), "panel", s.panelSize(), "recommendations", s.recommendations())
                    : Map.of("feedbackGiven", s.submitted(), "panel", s.panelSize(), "recommendations", "hidden until you give yours")));
            m.put("interviews", interviews.forApplication(actor, applicationId).stream()
                    .map(i -> {
                        Map<String, Object> row = interview(i);
                        row.put("feedback", summaries.get(i.id()));
                        return row;
                    })
                    .toList());
        }
        m.put("tests", tests.latestSubmitted(List.of(applicationId)).stream()
                .map(t -> Map.of("test", t.title(), "percent", Objects.requireNonNullElse(t.percent(), 0),
                        "passed", Boolean.TRUE.equals(t.passed()), "passMark", t.passPercent(),
                        "submittedAt", String.valueOf(t.submittedAt())))
                .toList());
        try {
            var insight = insights.insight(actor, applicationId);
            Map<String, Object> resume = new LinkedHashMap<>();
            resume.put("status", insight.status());
            resume.put("fitPercent", insight.fitPercent());
            resume.put("headline", insight.headline());
            if (insight.profile() != null) {
                var p = insight.profile();
                resume.put("currentRole", p.currentRole());
                resume.put("experienceMonths", p.experienceMonths());
                resume.put("education", p.education());
                resume.put("graduationYear", p.graduationYear());
                resume.put("skills", p.skills());
                resume.put("strengths", p.strengths());
                resume.put("gaps", p.gaps());
                resume.put("requirements", p.requirements());
            }
            m.put("resume", resume);
        } catch (RuntimeException e) {
            m.put("resume", "No résumé reading");
        }
        return json(m);
    }

    private static Map<String, Object> event(EventResponse e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", e.createdAt());
        m.put("type", e.type());
        if (e.fromStage() != null) {
            m.put("from", e.fromStage().getLabel());
        }
        if (e.toStage() != null) {
            m.put("to", e.toStage().getLabel());
        }
        if (e.note() != null) {
            m.put("note", e.note());
        }
        m.put("by", e.actorEmail());
        return m;
    }

    private String openingProfiles(AppUser actor, UUID jobId) {
        allow(actor, Capability.VIEW_CANDIDATES, "candidates");
        Map<UUID, ApplicationResponse> byId = new LinkedHashMap<>();
        tracker.listApplications(actor, jobId).forEach(a -> byId.put(a.id(), a));
        var response = insights.insights(actor, jobId);
        List<Map<String, Object>> rows = response.insights().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            ApplicationResponse a = byId.get(s.applicationId());
            m.put("applicationId", s.applicationId());
            m.put("name", a == null ? null : a.name());
            m.put("stage", a == null ? null : a.stageLabel());
            m.put("fitPercent", s.fitPercent());
            m.put("headline", s.headline());
            m.put("skills", s.skills());
            m.put("experienceMonths", s.experienceMonths());
            m.put("graduationYear", s.graduationYear());
            m.put("requirementsMet", s.met() + " of " + s.total());
            return m;
        }).toList();
        return json(Map.of("jobId", jobId, "aiReading", response.available(), "profiles", rows,
                "candidatesWithoutProfile", Math.max(0, byId.size() - rows.size())));
    }

    private String listInterviews(AppUser actor, String when) {
        allow(actor, Capability.VIEW_INTERVIEWS, "interviews");
        Object rows = switch (when) {
            case "recent" -> feedback.recent(actor).stream().map(r -> {
                Map<String, Object> m = interview(r.interview());
                m.put("feedbackGiven", r.submitted());
                m.put("panel", r.panelSize());
                m.put("yoursDue", r.canSubmit() && !r.mineSubmitted());
                return m;
            }).toList();
            case "feedback_due" -> feedback.due(actor).stream().map(AskService::interview).toList();
            default -> interviews.upcoming(actor).stream().map(AskService::interview).toList();
        };
        return json(Map.of("when", when, "interviews", rows));
    }

    private static Map<String, Object> interview(InterviewResponse i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("interviewId", i.id());
        m.put("applicationId", i.applicationId());
        m.put("jobId", i.jobId());
        m.put("candidate", i.candidateName());
        m.put("opening", i.jobTitle());
        m.put("startAt", i.startAt());
        m.put("endAt", i.endAt());
        m.put("interviewers", i.interviewers());
        m.put("status", i.status());
        if (i.cancelReason() != null) {
            m.put("cancelReason", i.cancelReason());
        }
        return m;
    }

    private String testResults(AppUser actor, String jobId) {
        allow(actor, Capability.MANAGE_JOBS, "tests");
        if (jobId == null) {
            return json(Map.of("newResults", tests.newResults(actor).stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("candidate", r.candidateName());
                m.put("applicationId", r.invite().applicationId());
                m.put("jobId", r.jobId());
                m.put("opening", r.jobTitle());
                m.put("test", r.invite().title());
                m.put("percent", r.invite().percent());
                m.put("passed", r.invite().passed());
                m.put("submittedAt", r.invite().submittedAt());
                return m;
            }).toList()));
        }
        UUID job = UUID.fromString(jobId);
        Map<UUID, String> names = new LinkedHashMap<>();
        tracker.listApplications(actor, job).forEach(a -> names.put(a.id(), a.name()));
        return json(Map.of("tests", tests.forJob(actor, job).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("candidate", names.get(t.applicationId()));
            m.put("applicationId", t.applicationId());
            m.put("test", t.title());
            m.put("status", t.status());
            m.put("percent", t.percent());
            m.put("passed", t.passed());
            m.put("passMark", t.passPercent());
            m.put("sentAt", t.sentAt());
            m.put("submittedAt", t.submittedAt());
            return m;
        }).toList()));
    }

    private String recentActivity(AppUser actor) {
        allow(actor, Capability.VIEW_CANDIDATES, "candidates");
        return json(Map.of("activity", tracker.dashboard(actor).recentActivity().stream().map(e -> {
            Map<String, Object> m = event(e);
            m.put("candidate", e.candidateName());
            m.put("opening", e.jobTitle());
            m.put("applicationId", e.applicationId());
            return m;
        }).toList()));
    }

    // ---- helpers ----

    /** A lookup the asker can't do comes back as a plain message, not an audited denial. */
    private void allow(AppUser actor, Capability capability, String what) {
        if (!accessPolicy.has(actor, capability)) {
            throw new IllegalArgumentException("Not allowed: this person can't see " + what + " in the ATS.");
        }
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String text(Map<String, Object> input, String key) {
        Object v = input.get(key);
        return v == null || !StringUtils.hasText(v.toString()) ? null : v.toString().strip();
    }

    private static UUID uuid(Map<String, Object> input, String key) {
        String v = text(input, key);
        if (v == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(key + " must be an id returned by another lookup");
        }
    }

    private AskConversation own(AppUser actor, UUID id) {
        return conversations.findByIdAndOwnerEmail(id, actor.getEmail())
                .orElseThrow(() -> new NotFoundException("Chat not found"));
    }

    private Conversation view(AskConversation c) {
        return new Conversation(c.getId(), c.getTitle(), read(c), c.getUpdatedAt());
    }

    static String title(String question) {
        String t = question.replaceAll("\\s+", " ").strip();
        return t.length() <= 60 ? t : t.substring(0, 57).replaceAll("\\s\\S*$", "") + "…";
    }

    private List<ChatMessage> read(AskConversation c) {
        try {
            return objectMapper.readValue(c.getMessagesJson(), new TypeReference<List<ChatMessage>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Object value) {
        return write(value);
    }

    private static String resource(String path) {
        try (InputStream in = AskService.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
