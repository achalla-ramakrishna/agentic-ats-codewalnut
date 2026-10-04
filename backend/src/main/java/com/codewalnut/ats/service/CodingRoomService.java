package com.codewalnut.ats.service;

import com.codewalnut.ats.bank.CodingBank;
import com.codewalnut.ats.bank.TechBank;
import com.codewalnut.ats.client.CodeRunner;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.CodingRoom;
import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewStatus;
import com.codewalnut.ats.dto.AssessmentDtos.CodingSpec;
import com.codewalnut.ats.dto.AssessmentDtos.RunCodeRequest;
import com.codewalnut.ats.dto.AssessmentDtos.RunCodeResult;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
import com.codewalnut.ats.dto.CodingRoomDtos.CandidateRoom;
import com.codewalnut.ats.dto.CodingRoomDtos.PastProblem;
import com.codewalnut.ats.dto.CodingRoomDtos.Problem;
import com.codewalnut.ats.dto.CodingRoomDtos.ProblemOption;
import com.codewalnut.ats.dto.CodingRoomDtos.ProblemRequest;
import com.codewalnut.ats.dto.CodingRoomDtos.RoomPage;
import com.codewalnut.ats.dto.CodingRoomDtos.RoomView;
import com.codewalnut.ats.dto.CodingRoomDtos.SaveCodeRequest;
import com.codewalnut.ats.repository.CodingRoomRepository;
import com.codewalnut.ats.repository.InterviewRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Live coding rooms (INT-36…, ADR-0020). An interviewer opens a room from an interview with a
 * built-in or typed problem; the candidate opens a private link, signs in with Google (as the
 * candidate on the application), writes code and runs it on the sample tests in the same Judge0
 * sandbox the online tests use. The panel watches the code by polling and keeps private notes.
 * Screen sharing and talking stay on Google Meet. Only sample tests run here: it's for watching
 * how someone codes, not for automatic grading.
 */
@Service
@RequiredArgsConstructor
public class CodingRoomService {

    /** Runs allowed per room, so a stuck loop of clicks can't flood the sandbox. */
    static final int MAX_RUNS = 200;

    private final CodingRoomRepository roomRepository;
    private final InterviewRepository interviewRepository;
    private final CodeRunService codeRunService;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ---- staff ----

    @Transactional(readOnly = true)
    public List<ProblemOption> problems(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        Map<String, String> topics = new LinkedHashMap<>();
        for (TechBank.Topic t : CodingBank.topics()) {
            topics.put(t.id(), t.name());
        }
        List<String> order = new ArrayList<>(topics.keySet());
        return CodingBank.problems().stream()
                .sorted((a, b) -> order.indexOf(a.topicId()) != order.indexOf(b.topicId())
                        ? Integer.compare(order.indexOf(a.topicId()), order.indexOf(b.topicId()))
                        : a.difficulty().compareTo(b.difficulty()))
                .map(p -> new ProblemOption(p.id(), p.title(), p.difficulty().name(), topics.get(p.topicId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomPage page(AppUser actor, UUID interviewId) {
        Interview interview = visibleInterview(actor, interviewId);
        boolean manage = canManage(actor, interview);
        RoomView view = roomRepository.findByInterviewId(interviewId).map(r -> view(r, interview, manage)).orElse(null);
        return new RoomPage(view, manage, codeRunService.available(),
                StringUtils.hasText(interview.getApplication().getCandidate().getEmail()));
    }

    /** Opens the interview's room with a problem, or switches an open room to a new problem. */
    @Transactional
    public RoomPage start(AppUser actor, UUID interviewId, ProblemRequest request) {
        Interview interview = visibleInterview(actor, interviewId);
        requireManage(actor, interview);
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw new IllegalArgumentException("This interview was cancelled");
        }
        if (!StringUtils.hasText(interview.getApplication().getCandidate().getEmail())) {
            throw new IllegalArgumentException("email: add the candidate's email first. They sign in with it to open the room");
        }
        Built problem = build(request);
        CodingRoom room = roomRepository.findByInterviewId(interviewId).orElse(null);
        if (room == null) {
            room = CodingRoom.builder().interviewId(interviewId).token(PublicLinks.newSlug() + PublicLinks.newSlug())
                    .createdBy(actor.getEmail()).language("java").build();
            auditService.record(actor, AuditAction.CODING_ROOM_STARTED, "Interview", interviewId, Map.of("problem", problem.title()));
        } else {
            archiveCurrent(room);
        }
        room.setStatus(CodingRoom.Status.OPEN);
        room.setEndedAt(null);
        room.setProblemId(problem.id());
        room.setTitle(problem.title());
        room.setStatement(problem.statement());
        room.setSpecJson(write(problem.spec()));
        if (!problem.spec().languages().contains(room.getLanguage())) {
            room.setLanguage(problem.spec().languages().get(0));
        }
        room.setCode(problem.spec().starter().get(room.getLanguage()));
        room.setCodeUpdatedAt(null);
        room.setLastRunJson(null);
        roomRepository.save(room);
        return page(actor, interviewId);
    }

    @Transactional
    public RoomPage end(AppUser actor, UUID interviewId) {
        Interview interview = visibleInterview(actor, interviewId);
        requireManage(actor, interview);
        CodingRoom room = roomRepository.findByInterviewId(interviewId).orElseThrow(() -> new NotFoundException("No coding room yet"));
        if (room.getStatus() == CodingRoom.Status.OPEN) {
            room.setStatus(CodingRoom.Status.ENDED);
            room.setEndedAt(Instant.now());
            auditService.record(actor, AuditAction.CODING_ROOM_ENDED, "Interview", interviewId, Map.of("runs", room.getRunCount()));
        }
        return page(actor, interviewId);
    }

    @Transactional
    public RoomPage notes(AppUser actor, UUID interviewId, String notes) {
        Interview interview = visibleInterview(actor, interviewId);
        requireManage(actor, interview);
        CodingRoom room = roomRepository.findByInterviewId(interviewId).orElseThrow(() -> new NotFoundException("No coding room yet"));
        room.setNotes(StringUtils.hasText(notes) ? notes.strip() : null);
        return page(actor, interviewId);
    }

    /** The panel can run the candidate's current code too, e.g. after the candidate stops. */
    @Transactional
    public RunCodeResult staffRun(AppUser actor, UUID interviewId) {
        Interview interview = visibleInterview(actor, interviewId);
        requireManage(actor, interview);
        CodingRoom room = roomRepository.findByInterviewId(interviewId).orElseThrow(() -> new NotFoundException("No coding room yet"));
        RunCodeResult result = codeRunService.runOnSamples(spec(room), room.getLanguage(), room.getCode(), -1);
        room.setLastRunJson(write(result));
        return result;
    }

    // ---- candidate ----

    @Transactional
    public CandidateRoom candidateView(CandidateAccount account, String token) {
        CodingRoom room = candidateRoom(account, token);
        room.setCandidateSeenAt(Instant.now());
        return candidateView(room);
    }

    @Transactional
    public CandidateRoom save(CandidateAccount account, String token, SaveCodeRequest request) {
        CodingRoom room = candidateRoom(account, token);
        requireOpen(room);
        setCode(room, request.language(), request.code());
        return candidateView(room);
    }

    @Transactional
    public CandidateRoom run(CandidateAccount account, String token, RunCodeRequest request) {
        CodingRoom room = candidateRoom(account, token);
        requireOpen(room);
        if (room.getRunCount() >= MAX_RUNS) {
            throw new IllegalArgumentException("You've used all " + MAX_RUNS + " runs in this room. Tell your interviewer.");
        }
        setCode(room, request.language(), request.source());
        room.setRunCount(room.getRunCount() + 1);
        RunCodeResult result = codeRunService.runOnSamples(spec(room), request.language(), request.source(), MAX_RUNS - room.getRunCount());
        room.setLastRunJson(write(result));
        return candidateView(room);
    }

    // ---- helpers ----

    private record Built(String id, String title, String statement, CodingSpec spec) {}

    private Built build(ProblemRequest request) {
        if (request != null && StringUtils.hasText(request.problemId())) {
            CodingBank.Problem p = CodingBank.problems().stream().filter(x -> x.id().equals(request.problemId().strip())).findFirst()
                    .orElseThrow(() -> new NotFoundException("Problem not found"));
            return new Built(p.id(), p.title(), p.statement().strip(), p.spec());
        }
        if (request == null || !StringUtils.hasText(request.title()) || !StringUtils.hasText(request.statement())) {
            throw new IllegalArgumentException("problem: pick a built-in problem, or give a title and a statement");
        }
        List<TestCase> samples = StringUtils.hasText(request.sampleInput()) || StringUtils.hasText(request.sampleOutput())
                ? List.of(new TestCase(nl(request.sampleInput()), nl(request.sampleOutput())))
                : List.of();
        Map<String, String> starter = new LinkedHashMap<>();
        for (String l : CodeRunner.LANGUAGES) {
            starter.put(l, CodingSpecs.defaultStarter(l));
        }
        CodingSpec spec = new CodingSpec(CodeRunner.LANGUAGES, starter, samples, 2.0, 256, null, null, null);
        return new Built(null, request.title().strip(), request.statement().strip(), spec);
    }

    private static String nl(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", "\n");
        return t.endsWith("\n") || t.isEmpty() ? t : t + "\n";
    }

    private void setCode(CodingRoom room, String language, String code) {
        String lang = language.strip().toLowerCase(Locale.ROOT);
        if (!spec(room).languages().contains(lang)) {
            throw new IllegalArgumentException("language: choose one of " + String.join(", ", spec(room).languages()));
        }
        room.setLanguage(lang);
        room.setCode(code);
        room.setCodeUpdatedAt(Instant.now());
    }

    private void archiveCurrent(CodingRoom room) {
        List<PastProblem> history = new ArrayList<>(history(room));
        RunCodeResult last = room.getLastRunJson() == null ? null : read(room.getLastRunJson(), new TypeReference<RunCodeResult>() {});
        boolean touched = room.getCodeUpdatedAt() != null || last != null;
        if (touched) {
            history.add(new PastProblem(room.getTitle(), room.getLanguage(), room.getCode(), last == null ? null : last.passed(),
                    last == null ? null : last.total()));
            room.setHistoryJson(write(history));
        }
    }

    private CodingRoom candidateRoom(CandidateAccount account, String token) {
        CodingRoom room = roomRepository.findByToken(token).orElseThrow(() -> new NotFoundException("Coding room not found"));
        Interview interview = interviewRepository.findById(room.getInterviewId()).orElseThrow(() -> new NotFoundException("Coding room not found"));
        String candidateEmail = interview.getApplication().getCandidate().getEmail();
        if (candidateEmail == null || !candidateEmail.equalsIgnoreCase(account.getEmail())) {
            // Same answer as a wrong link: nothing about the room leaks to anyone else.
            throw new NotFoundException("Coding room not found");
        }
        return room;
    }

    private static void requireOpen(CodingRoom room) {
        if (room.getStatus() != CodingRoom.Status.OPEN) {
            throw new ConflictException("This coding room has ended. Thanks!");
        }
    }

    private CandidateRoom candidateView(CodingRoom room) {
        Interview interview = interviewRepository.findById(room.getInterviewId()).orElseThrow();
        RunCodeResult last = room.getLastRunJson() == null ? null : read(room.getLastRunJson(), new TypeReference<RunCodeResult>() {});
        return new CandidateRoom(room.getStatus().name(), interview.getApplication().getJob().getTitle(), problem(room), room.getLanguage(),
                room.getCode(), last, Math.max(0, MAX_RUNS - room.getRunCount()), codeRunService.available());
    }

    private RoomView view(CodingRoom room, Interview interview, boolean manage) {
        RunCodeResult last = room.getLastRunJson() == null ? null : read(room.getLastRunJson(), new TypeReference<RunCodeResult>() {});
        return new RoomView(room.getId(), room.getInterviewId(), interview.getApplication().getCandidate().getName(), "/coding/" + room.getToken(),
                room.getStatus().name(), problem(room), room.getLanguage(), room.getCode(), room.getCodeUpdatedAt(), room.getCandidateSeenAt(),
                last, room.getRunCount(), history(room), room.getNotes(), manage, room.getEndedAt());
    }

    private Problem problem(CodingRoom room) {
        CodingSpec s = spec(room);
        return new Problem(room.getProblemId(), room.getTitle(), room.getStatement(), s.inputFormat(), s.outputFormat(), s.samples(),
                s.languages(), s.starter());
    }

    private CodingSpec spec(CodingRoom room) {
        return read(room.getSpecJson(), new TypeReference<CodingSpec>() {});
    }

    private List<PastProblem> history(CodingRoom room) {
        return room.getHistoryJson() == null ? List.of() : read(room.getHistoryJson(), new TypeReference<List<PastProblem>>() {});
    }

    /** Hiring staff see any interview's room; interviewers only for interviews they're on. */
    private Interview visibleInterview(AppUser actor, UUID interviewId) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        Interview i = interviewRepository.findById(interviewId).orElseThrow(() -> new NotFoundException("Interview not found"));
        if (!accessPolicy.has(actor, Capability.VIEW_CANDIDATES) && !InterviewFeedbackService.onPanel(i, actor.getEmail().toLowerCase(Locale.ROOT))) {
            throw new NotFoundException("Interview not found");
        }
        return i;
    }

    private boolean canManage(AppUser actor, Interview i) {
        return InterviewFeedbackService.onPanel(i, actor.getEmail().toLowerCase(Locale.ROOT)) || accessPolicy.has(actor, Capability.MANAGE_JOBS);
    }

    private void requireManage(AppUser actor, Interview i) {
        if (!canManage(actor, i)) {
            throw new org.springframework.security.access.AccessDeniedException("Only the interview's panel can run its coding room");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
