package com.codewalnut.ats.service;

import com.codewalnut.ats.client.AssessmentDraft;
import com.codewalnut.ats.client.AssessmentDrafter;
import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.dto.AssessmentDtos.AssessmentDetail;
import com.codewalnut.ats.dto.AssessmentDtos.AssessmentSummary;
import com.codewalnut.ats.dto.AssessmentDtos.CreateAssessmentRequest;
import com.codewalnut.ats.dto.AssessmentDtos.DraftRequest;
import com.codewalnut.ats.dto.AssessmentDtos.DraftResult;
import com.codewalnut.ats.dto.AssessmentDtos.QuestionRequest;
import com.codewalnut.ats.dto.AssessmentDtos.QuestionView;
import com.codewalnut.ats.dto.AssessmentDtos.UpdateAssessmentRequest;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.AssessmentQuestionRepository;
import com.codewalnut.ats.repository.AssessmentRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The test library: tests, their questions, and AI-drafted questions for a person to review
 * (ADR-0011). A test's questions can change only while it is a draft, so every candidate who
 * takes it gets the same questions.
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    static final int MAX_QUESTIONS = 100;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final AssessmentDrafter drafter;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /** Everyone who sends tests sees the list; only test managers see the questions. */
    @Transactional(readOnly = true)
    public List<AssessmentSummary> list(AppUser actor) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        List<Assessment> all = assessmentRepository.findAllByOrderByUpdatedAtDesc();
        Map<UUID, long[]> counts = new HashMap<>();
        if (!all.isEmpty()) {
            for (Object[] row : questionRepository.countsFor(all.stream().map(Assessment::getId).toList())) {
                counts.put((UUID) row[0], new long[] {(Long) row[1], row[2] == null ? 0 : ((Number) row[2]).longValue()});
            }
        }
        return all.stream().map(a -> summary(a, counts.getOrDefault(a.getId(), new long[] {0, 0}))).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDetail get(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        return detail(assessment(id));
    }

    @Transactional
    public AssessmentDetail create(AppUser actor, CreateAssessmentRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment saved = assessmentRepository.save(Assessment.builder()
                .title(request.title().strip())
                .category(request.category())
                .description(blankToNull(request.description()))
                .durationMinutes(request.durationMinutes())
                .passPercent(request.passPercent())
                .status(Assessment.Status.DRAFT)
                .createdBy(actor.getEmail())
                .build());
        auditService.record(actor, AuditAction.ASSESSMENT_CREATED, "Assessment", saved.getId(), Map.of("category", saved.getCategory()));
        return detail(saved);
    }

    @Transactional
    public AssessmentDetail update(AppUser actor, UUID id, UpdateAssessmentRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = assessment(id);
        boolean draft = a.getStatus() == Assessment.Status.DRAFT;
        if (request.title() != null) {
            a.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            a.setDescription(blankToNull(request.description()));
        }
        if (request.category() != null || request.durationMinutes() != null || request.passPercent() != null) {
            if (!draft) {
                throw new IllegalArgumentException("Category, time limit and pass mark can only change while the test is a draft. Duplicate it to make a new version.");
            }
            if (request.category() != null) {
                a.setCategory(request.category());
            }
            if (request.durationMinutes() != null) {
                a.setDurationMinutes(request.durationMinutes());
            }
            if (request.passPercent() != null) {
                a.setPassPercent(request.passPercent());
            }
        }
        a.setUpdatedAt(java.time.Instant.now());
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "Assessment", id, Map.of("change", "details"));
        return detail(a);
    }

    @Transactional
    public AssessmentDetail addQuestion(AppUser actor, UUID id, QuestionRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = editable(id);
        List<AssessmentQuestion> existing = questionRepository.findByAssessmentIdOrderByPositionAsc(id);
        if (existing.size() >= MAX_QUESTIONS) {
            throw new IllegalArgumentException("A test can have up to " + MAX_QUESTIONS + " questions");
        }
        AssessmentQuestion q = AssessmentQuestion.builder().assessmentId(id).position(existing.size() + 1).build();
        apply(q, normalise(request));
        questionRepository.save(q);
        touch(a);
        return detail(a);
    }

    @Transactional
    public AssessmentDetail updateQuestion(AppUser actor, UUID id, UUID questionId, QuestionRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = editable(id);
        AssessmentQuestion q = question(id, questionId);
        apply(q, normalise(request));
        q.setAiDrafted(false); // reviewed and edited by a person
        touch(a);
        return detail(a);
    }

    @Transactional
    public AssessmentDetail deleteQuestion(AppUser actor, UUID id, UUID questionId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = editable(id);
        questionRepository.delete(question(id, questionId));
        questionRepository.flush();
        int position = 1;
        for (AssessmentQuestion q : questionRepository.findByAssessmentIdOrderByPositionAsc(id)) {
            q.setPosition(position++);
        }
        touch(a);
        return detail(a);
    }

    /** Draft → ready to send. Locks the questions. */
    @Transactional
    public AssessmentDetail publish(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = assessment(id);
        if (a.getStatus() != Assessment.Status.DRAFT) {
            throw new IllegalArgumentException("Only a draft can be made ready");
        }
        if (questionRepository.countByAssessmentId(id) == 0) {
            throw new IllegalArgumentException("Add at least one question first");
        }
        a.setStatus(Assessment.Status.READY);
        touch(a);
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "Assessment", id, Map.of("status", "READY"));
        return detail(a);
    }

    /** Ready → draft again, only while nobody has been sent it. */
    @Transactional
    public AssessmentDetail unpublish(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = assessment(id);
        if (a.getStatus() == Assessment.Status.DRAFT) {
            return detail(a);
        }
        if (inviteRepository.countByAssessmentId(id) > 0) {
            throw new IllegalArgumentException("Candidates have already been sent this test. Duplicate it to change the questions.");
        }
        a.setStatus(Assessment.Status.DRAFT);
        touch(a);
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "Assessment", id, Map.of("status", "DRAFT"));
        return detail(a);
    }

    @Transactional
    public AssessmentDetail archive(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = assessment(id);
        a.setStatus(Assessment.Status.ARCHIVED);
        touch(a);
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "Assessment", id, Map.of("status", "ARCHIVED"));
        return detail(a);
    }

    /** A new draft with the same details and questions, to make a changed version. */
    @Transactional
    public AssessmentDetail duplicate(AppUser actor, UUID id) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = assessment(id);
        String title = a.getTitle().length() > 190 ? a.getTitle().substring(0, 190) : a.getTitle();
        Assessment copy = assessmentRepository.save(Assessment.builder()
                .title(title + " (copy)").category(a.getCategory()).description(a.getDescription())
                .durationMinutes(a.getDurationMinutes()).passPercent(a.getPassPercent())
                .status(Assessment.Status.DRAFT).createdBy(actor.getEmail()).build());
        for (AssessmentQuestion q : questionRepository.findByAssessmentIdOrderByPositionAsc(id)) {
            questionRepository.save(AssessmentQuestion.builder()
                    .assessmentId(copy.getId()).position(q.getPosition()).kind(q.getKind()).prompt(q.getPrompt())
                    .code(q.getCode()).optionsJson(q.getOptionsJson()).answerJson(q.getAnswerJson())
                    .points(q.getPoints()).explanation(q.getExplanation()).aiDrafted(q.isAiDrafted())
                    .figure(q.getFigure()).optionFiguresJson(q.getOptionFiguresJson()).section(q.getSection())
                    .topic(q.getTopic()).difficulty(q.getDifficulty()).bankQuestionId(q.getBankQuestionId()).build());
        }
        auditService.record(actor, AuditAction.ASSESSMENT_CREATED, "Assessment", copy.getId(), Map.of("copiedFrom", id));
        return detail(copy);
    }

    /** Adds AI-drafted questions to a draft, marked for review. Invalid drafts are dropped. */
    @Transactional
    public DraftResult draft(AppUser actor, UUID id, DraftRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = editable(id);
        if (!drafter.available()) {
            throw new CalendarException("AI question drafting isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
        }
        List<AssessmentQuestion> existing = questionRepository.findByAssessmentIdOrderByPositionAsc(id);
        int room = MAX_QUESTIONS - existing.size();
        if (room <= 0) {
            throw new IllegalArgumentException("A test can have up to " + MAX_QUESTIONS + " questions");
        }
        int count = Math.min(request.count(), room);
        AssessmentDraft draft = drafter.draft(new AssessmentDrafter.Request(a.getCategory().name(), a.getTitle(),
                request.topic(), request.level(), count,
                existing.stream().map(q -> q.getPrompt() + (q.getCode() == null ? "" : "\n" + q.getCode())).toList()));
        List<String> notes = new ArrayList<>();
        int position = existing.size();
        int added = 0;
        for (AssessmentDraft.Question d : draft.questions() == null ? List.<AssessmentDraft.Question>of() : draft.questions()) {
            if (added >= count) {
                break;
            }
            QuestionRequest candidate;
            try {
                candidate = normalise(new QuestionRequest(
                        AssessmentQuestion.Kind.valueOf(String.valueOf(d.kind()).strip().toUpperCase(java.util.Locale.ROOT)),
                        d.prompt(), d.code(), d.options(), d.correctOptions(), d.acceptedAnswers(),
                        d.points() == null ? 1 : Math.max(1, Math.min(10, d.points())), d.explanation()));
            } catch (IllegalArgumentException | NullPointerException e) {
                notes.add("Skipped a drafted question that wasn't complete.");
                continue;
            }
            AssessmentQuestion q = AssessmentQuestion.builder().assessmentId(id).position(++position).aiDrafted(true).build();
            apply(q, candidate);
            questionRepository.save(q);
            added++;
        }
        touch(a);
        auditService.record(actor, AuditAction.ASSESSMENT_DRAFTED, "Assessment", id, Map.of("added", added));
        return new DraftResult(added, notes, detail(a));
    }

    // ---- helpers shared with AssessmentInviteService ----

    List<String> options(AssessmentQuestion q) {
        return readList(q.getOptionsJson(), new TypeReference<List<String>>() {});
    }

    /** Pictures for the options, or null when the options are text. */
    List<String> optionFigures(AssessmentQuestion q) {
        return q.getOptionFiguresJson() == null ? null : readList(q.getOptionFiguresJson(), new TypeReference<List<String>>() {});
    }

    List<Integer> correct(AssessmentQuestion q) {
        return q.getKind() == AssessmentQuestion.Kind.SHORT_ANSWER ? List.of()
                : readList(q.getAnswerJson(), new TypeReference<List<Integer>>() {});
    }

    List<String> accepted(AssessmentQuestion q) {
        return q.getKind() == AssessmentQuestion.Kind.SHORT_ANSWER
                ? readList(q.getAnswerJson(), new TypeReference<List<String>>() {}) : List.of();
    }

    /** Points earned for the given answer: all or nothing. */
    int earned(AssessmentQuestion q, List<String> given) {
        if (given == null || given.isEmpty()) {
            return 0;
        }
        if (q.getKind() == AssessmentQuestion.Kind.SHORT_ANSWER) {
            String answer = normaliseAnswer(given.get(0));
            return !answer.isEmpty() && accepted(q).stream().map(AssessmentService::normaliseAnswer).anyMatch(answer::equals)
                    ? q.getPoints() : 0;
        }
        java.util.Set<Integer> picked = new java.util.HashSet<>();
        for (String g : given) {
            try {
                picked.add(Integer.parseInt(g.strip()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return picked.equals(new java.util.HashSet<>(correct(q))) ? q.getPoints() : 0;
    }

    /** Case, surrounding quotes and extra spaces don't matter for short answers. */
    static String normaliseAnswer(String s) {
        String t = s == null ? "" : s.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
            t = t.substring(1, t.length() - 1).strip();
        }
        return t;
    }

    AssessmentSummary summaryOf(Assessment a) {
        List<AssessmentQuestion> qs = questionRepository.findByAssessmentIdOrderByPositionAsc(a.getId());
        return summary(a, new long[] {qs.size(), qs.stream().mapToInt(AssessmentQuestion::getPoints).sum()});
    }

    // ---- private ----

    AssessmentDetail detail(Assessment a) {
        List<AssessmentQuestion> qs = questionRepository.findByAssessmentIdOrderByPositionAsc(a.getId());
        List<QuestionView> views = qs.stream()
                .map(q -> new QuestionView(q.getId(), q.getPosition(), q.getKind(), q.getPrompt(), q.getCode(),
                        options(q), correct(q), accepted(q), q.getPoints(), q.getExplanation(), q.isAiDrafted(),
                        q.getFigure(), optionFigures(q), q.getSection(), q.getTopic(), q.getDifficulty()))
                .toList();
        return new AssessmentDetail(summary(a, new long[] {qs.size(), qs.stream().mapToInt(AssessmentQuestion::getPoints).sum()}), views);
    }

    private AssessmentSummary summary(Assessment a, long[] counts) {
        return new AssessmentSummary(a.getId(), a.getTitle(), a.getCategory(), a.getDescription(), a.getDurationMinutes(),
                a.getPassPercent(), a.getStatus(), (int) counts[0], (int) counts[1],
                inviteRepository.countByAssessmentId(a.getId()), a.getUpdatedAt());
    }

    /** Validates and tidies a question; throws IllegalArgumentException with a field message. */
    QuestionRequest normalise(QuestionRequest r) {
        String prompt = r.prompt() == null ? "" : r.prompt().strip();
        if (prompt.isEmpty()) {
            throw new IllegalArgumentException("prompt: write the question");
        }
        String code = blankToNull(r.code() == null ? null : r.code().replaceAll("^```\\w*\\n?|\\n?```$", ""));
        if (r.kind() == AssessmentQuestion.Kind.SHORT_ANSWER) {
            List<String> accepted = r.acceptedAnswers() == null ? List.of() : r.acceptedAnswers().stream()
                    .filter(StringUtils::hasText).map(String::strip).distinct().toList();
            if (accepted.isEmpty()) {
                throw new IllegalArgumentException("acceptedAnswers: add at least one accepted answer");
            }
            return new QuestionRequest(r.kind(), prompt, code, List.of(), List.of(), accepted, r.points(), blankToNull(r.explanation()),
                    com.codewalnut.ats.bank.Figures.check(r.figure()));
        }
        List<String> options = r.options() == null ? List.of() : r.options().stream()
                .map(o -> o == null ? "" : o.strip()).toList();
        if (options.size() < 2 || options.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("options: give at least two options, none empty");
        }
        if (new LinkedHashSet<>(options).size() != options.size()) {
            throw new IllegalArgumentException("options: each option must be different");
        }
        List<Integer> correct = r.correct() == null ? List.of() : r.correct().stream().distinct().sorted().toList();
        if (correct.isEmpty() || correct.stream().anyMatch(i -> i == null || i < 0 || i >= options.size())) {
            throw new IllegalArgumentException("correct: mark the right option");
        }
        if (r.kind() == AssessmentQuestion.Kind.SINGLE_CHOICE && correct.size() != 1) {
            throw new IllegalArgumentException("correct: a single-choice question has exactly one right option");
        }
        return new QuestionRequest(r.kind(), prompt, code, options, correct, List.of(), r.points(), blankToNull(r.explanation()),
                com.codewalnut.ats.bank.Figures.check(r.figure()));
    }

    void apply(AssessmentQuestion q, QuestionRequest r) {
        List<String> oldOptions = q.getOptionsJson() == null ? List.of() : options(q);
        if (q.getOptionFiguresJson() != null && (r.kind() == AssessmentQuestion.Kind.SHORT_ANSWER || r.options().size() != oldOptions.size())) {
            q.setOptionFiguresJson(null); // option pictures only fit the same set of options
        }
        q.setFigure(r.figure());
        q.setKind(r.kind());
        q.setPrompt(r.prompt());
        q.setCode(r.code());
        q.setOptionsJson(r.kind() == AssessmentQuestion.Kind.SHORT_ANSWER ? null : write(r.options()));
        q.setAnswerJson(write(r.kind() == AssessmentQuestion.Kind.SHORT_ANSWER ? r.acceptedAnswers() : r.correct()));
        q.setPoints(r.points());
        q.setExplanation(r.explanation());
    }

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    void touch(Assessment a) {
        a.setUpdatedAt(java.time.Instant.now());
    }

    Assessment editable(UUID id) {
        Assessment a = assessment(id);
        if (a.getStatus() != Assessment.Status.DRAFT) {
            throw new IllegalArgumentException("This test is ready to send, so its questions are locked. Duplicate it to make changes.");
        }
        return a;
    }

    private Assessment assessment(UUID id) {
        return assessmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Test not found"));
    }

    private AssessmentQuestion question(UUID assessmentId, UUID questionId) {
        AssessmentQuestion q = questionRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        if (!q.getAssessmentId().equals(assessmentId)) {
            throw new NotFoundException("Question not found");
        }
        return q;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }
}
