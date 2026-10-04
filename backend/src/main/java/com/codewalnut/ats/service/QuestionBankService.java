package com.codewalnut.ats.service;

import com.codewalnut.ats.bank.AptitudeBank;
import com.codewalnut.ats.bank.Presets;
import com.codewalnut.ats.bank.Seed;
import com.codewalnut.ats.bank.Svg;
import com.codewalnut.ats.bank.TechBank;
import com.codewalnut.ats.client.AssessmentDraft;
import com.codewalnut.ats.client.AssessmentDrafter;
import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.BankQuestion;
import com.codewalnut.ats.dto.AssessmentDtos.AssessmentDetail;
import com.codewalnut.ats.dto.AssessmentDtos.QuestionRequest;
import com.codewalnut.ats.dto.BankDtos.AddFromBankRequest;
import com.codewalnut.ats.dto.BankDtos.BankDraftRequest;
import com.codewalnut.ats.dto.BankDtos.BankDraftResult;
import com.codewalnut.ats.dto.BankDtos.BankOverview;
import com.codewalnut.ats.dto.BankDtos.BankPage;
import com.codewalnut.ats.dto.BankDtos.BankQuestionRequest;
import com.codewalnut.ats.dto.BankDtos.BankQuestionView;
import com.codewalnut.ats.dto.BankDtos.BuildRequest;
import com.codewalnut.ats.dto.BankDtos.Count;
import com.codewalnut.ats.dto.BankDtos.TopicCount;
import com.codewalnut.ats.dto.BankDtos.TopicGuide;
import com.codewalnut.ats.dto.BankDtos.TopicPlan;
import com.codewalnut.ats.repository.AssessmentQuestionRepository;
import com.codewalnut.ats.repository.AssessmentRepository;
import com.codewalnut.ats.repository.BankQuestionRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The question bank and the test-paper builder (ADR-0014). Papers copy questions out of the bank,
 * so later bank edits never change a test someone was sent. AI drafts wait for a person's review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankQuestionRepository bankRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentService assessmentService;
    private final AssessmentDrafter drafter;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.codewalnut.ats.service.CodingSpecs codingSpecs;

    // ---- the built-in bank ----

    /**
     * Adds any built-in questions not yet in the database and archives built-in questions the
     * current bank no longer generates (e.g. the first, smaller bank). Never edits a question's
     * content; tests keep their own copies, so archiving changes nothing already sent.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadBuiltIn() {
        List<Seed> seeds = new ArrayList<>(AptitudeBank.all());
        seeds.addAll(TechBank.all());
        seeds.addAll(com.codewalnut.ats.bank.CodingBank.all());
        Set<String> current = seeds.stream().map(Seed::key).collect(Collectors.toSet());
        Set<String> have = bankRepository.findBuiltinKeys();
        List<BankQuestion> fresh = new ArrayList<>();
        for (Seed seed : seeds) {
            if (have.contains(seed.key())) {
                continue;
            }
            fresh.add(BankQuestion.builder()
                    .area(seed.area()).section(seed.section()).topic(seed.topic()).difficulty(seed.difficulty())
                    .kind(seed.kind()).prompt(seed.prompt()).code(seed.code()).figure(seed.figure())
                    .optionsJson(assessmentService.write(seed.options()))
                    .optionFiguresJson(seed.optionFigures() == null ? null : assessmentService.write(seed.optionFigures()))
                    .answerJson(seed.kind() == AssessmentQuestion.Kind.CODING ? seed.testsJson()
                            : assessmentService.write(seed.kind() == AssessmentQuestion.Kind.SHORT_ANSWER ? seed.accepted() : seed.correct()))
                    .codingJson(seed.codingJson())
                    .points(seed.points()).explanation(seed.explanation())
                    .source(BankQuestion.Source.BUILT_IN).builtinKey(seed.key()).status(BankQuestion.Status.ACTIVE)
                    .createdBy("system").build());
        }
        bankRepository.saveAll(fresh);
        int archived = 0;
        for (BankQuestion old : bankRepository.findBySourceAndStatus(BankQuestion.Source.BUILT_IN, BankQuestion.Status.ACTIVE)) {
            if (old.getBuiltinKey() != null && !current.contains(old.getBuiltinKey())) {
                old.setStatus(BankQuestion.Status.ARCHIVED);
                archived++;
            }
        }
        if (!fresh.isEmpty() || archived > 0) {
            log.info("Built-in bank: added {} questions, archived {} retired ones", fresh.size(), archived);
        }
    }

    // ---- browsing ----

    @Transactional(readOnly = true)
    public BankOverview overview(AppUser actor, Assessment.Category requested) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment.Category area = requested == null ? Assessment.Category.APTITUDE : requested;
        List<Count> counts = bankRepository.countActive().stream()
                .map(r -> new Count((Assessment.Category) r[0], (BankQuestion.Section) r[1], ((BankQuestion.Section) r[1]).getLabel(),
                        (BankQuestion.Difficulty) r[2], (Long) r[3]))
                .toList();
        Map<String, long[]> topics = new LinkedHashMap<>();
        Map<String, BankQuestion.Section> sectionOf = new LinkedHashMap<>();
        for (BankQuestion q : bankRepository.findByAreaAndStatusOrderBySectionAscTopicAscDifficultyAsc(area,
                BankQuestion.Status.ACTIVE)) {
            String k = q.getSection() + "/" + q.getTopic();
            sectionOf.put(k, q.getSection());
            long[] c = topics.computeIfAbsent(k, x -> new long[2]);
            c[0]++;
            if (q.getFigure() != null || q.getOptionFiguresJson() != null) {
                c[1]++;
            }
        }
        List<TopicCount> topicCounts = topics.entrySet().stream()
                .map(e -> new TopicCount(sectionOf.get(e.getKey()), e.getKey().substring(e.getKey().indexOf('/') + 1), e.getValue()[0], e.getValue()[1]))
                .toList();
        return new BankOverview(counts, topicCounts, Presets.forArea(area), guide(area));
    }

    /** The built-in topics in their catalogue order, then any topics added by hand. */
    private List<TopicGuide> guide(Assessment.Category area) {
        Map<String, long[]> levels = new LinkedHashMap<>();
        Map<String, BankQuestion.Section> sectionOf = new LinkedHashMap<>();
        for (BankQuestion q : bankRepository.findByAreaAndStatusOrderBySectionAscTopicAscDifficultyAsc(area,
                BankQuestion.Status.ACTIVE)) {
            String k = q.getSection() + "/" + q.getTopic();
            sectionOf.put(k, q.getSection());
            levels.computeIfAbsent(k, x -> new long[3])[q.getDifficulty().ordinal()]++;
        }
        List<TopicGuide> out = new ArrayList<>();
        if (area == Assessment.Category.APTITUDE) {
            for (AptitudeBank.Topic t : AptitudeBank.TOPICS) {
                long[] c = levels.remove(t.section() + "/" + t.name());
                c = c == null ? new long[3] : c;
                out.add(new TopicGuide(t.id(), t.section(), t.section().getLabel(), t.section().getLevel(), t.name(), t.covers(), t.example(), c[0], c[1], c[2]));
            }
        }
        for (TechBank.Topic t : area == Assessment.Category.CODING ? com.codewalnut.ats.bank.CodingBank.topics() : TechBank.topics(area)) {
            long[] c = levels.remove(t.section() + "/" + t.name());
            c = c == null ? new long[3] : c;
            out.add(new TopicGuide(t.id(), t.section(), t.section().getLabel(), t.section().getLevel(), t.name(), t.covers(), t.example(), c[0], c[1], c[2]));
        }
        levels.forEach((k, c) -> {
            String name = k.substring(k.indexOf('/') + 1);
            out.add(new TopicGuide("custom:" + name, sectionOf.get(k), sectionOf.get(k).getLabel(), sectionOf.get(k).getLevel(), name, "", "", c[0], c[1], c[2]));
        });
        return out;
    }

    @Transactional(readOnly = true)
    public BankPage list(AppUser actor, Assessment.Category area, BankQuestion.Section section, BankQuestion.Difficulty difficulty,
            String topic, boolean picturesOnly, BankQuestion.Status status, String q, int page, int size) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        String needle = StringUtils.hasText(q) ? q.strip().toLowerCase(Locale.ROOT) : null;
        BankQuestion.Status wanted = status == null ? BankQuestion.Status.ACTIVE : status;
        List<BankQuestion> all = bankRepository.findByAreaAndStatusOrderBySectionAscTopicAscDifficultyAsc(
                area == null ? Assessment.Category.APTITUDE : area, wanted).stream()
                .filter(b -> section == null || b.getSection() == section)
                .filter(b -> difficulty == null || b.getDifficulty() == difficulty)
                .filter(b -> !StringUtils.hasText(topic) || b.getTopic().equalsIgnoreCase(topic))
                .filter(b -> !picturesOnly || b.getFigure() != null || b.getOptionFiguresJson() != null)
                .filter(b -> needle == null || b.getPrompt().toLowerCase(Locale.ROOT).contains(needle)
                        || b.getTopic().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        int from = Math.min(all.size(), Math.max(0, page) * size);
        int to = Math.min(all.size(), from + size);
        return new BankPage(all.subList(from, to).stream().map(this::view).toList(), all.size());
    }

    // ---- editing ----

    @Transactional
    public BankQuestionView create(AppUser actor, BankQuestionRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        checkSection(request.area(), request.section());
        QuestionRequest q = assessmentService.normalise(request.question());
        BankQuestion saved = bankRepository.save(apply(BankQuestion.builder()
                .area(request.area()).section(request.section()).topic(request.topic().strip()).difficulty(request.difficulty())
                .source(BankQuestion.Source.MANUAL).status(BankQuestion.Status.ACTIVE).createdBy(actor.getEmail()).build(), q));
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "BankQuestion", saved.getId(), Map.of("change", "created"));
        return view(saved);
    }

    /** Edits a question; approving an AI draft (REVIEW) makes it usable. */
    @Transactional
    public BankQuestionView update(AppUser actor, UUID id, BankQuestionRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        BankQuestion b = bank(id);
        QuestionRequest q = assessmentService.normalise(request.question());
        checkSection(request.area(), request.section());
        b.setArea(request.area());
        b.setSection(request.section());
        b.setTopic(request.topic().strip());
        b.setDifficulty(request.difficulty());
        List<String> oldOptions = b.getOptionsJson() == null ? List.of() : assessmentService.readList(b.getOptionsJson(), new TypeReference<List<String>>() {});
        if (b.getOptionFiguresJson() != null && (q.kind() == AssessmentQuestion.Kind.SHORT_ANSWER || q.options().size() != oldOptions.size())) {
            b.setOptionFiguresJson(null);
        }
        apply(b, q);
        if (b.getStatus() == BankQuestion.Status.REVIEW) {
            b.setStatus(BankQuestion.Status.ACTIVE);
        }
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "BankQuestion", id, Map.of("change", "edited"));
        return view(b);
    }

    @Transactional
    public BankQuestionView setStatus(AppUser actor, UUID id, BankQuestion.Status status) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        BankQuestion b = bank(id);
        b.setStatus(status);
        auditService.record(actor, AuditAction.ASSESSMENT_UPDATED, "BankQuestion", id, Map.of("status", status));
        return view(b);
    }

    /** AI-drafted questions for the bank, held for review. Charts are drawn by the app from the AI's numbers. */
    @Transactional
    public BankDraftResult draft(AppUser actor, BankDraftRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        if (!drafter.available()) {
            throw new CalendarException("AI question drafting isn't set up yet. An admin needs to add ANTHROPIC_API_KEY.");
        }
        Assessment.Category area = request.area() == null ? Assessment.Category.APTITUDE : request.area();
        checkSection(area, request.section());
        List<String> avoid = bankRepository.findByAreaAndSectionAndDifficultyAndStatus(area, request.section(),
                        request.difficulty(), BankQuestion.Status.ACTIVE).stream()
                .filter(b -> b.getTopic().equalsIgnoreCase(request.topic()))
                .map(BankQuestion::getPrompt).limit(40).toList();
        String level = area == Assessment.Category.APTITUDE ? " (freshers' campus aptitude test)"
                : " (for " + request.section().getLevel().toLowerCase(Locale.ROOT) + " developers)";
        AssessmentDraft draft = drafter.draft(new AssessmentDrafter.Request(area.name(), request.section().getLabel(), request.topic(),
                request.difficulty().name().toLowerCase(Locale.ROOT) + level, request.count(), avoid));
        List<String> notes = new ArrayList<>();
        List<BankQuestionView> added = new ArrayList<>();
        for (AssessmentDraft.Question d : draft.questions() == null ? List.<AssessmentDraft.Question>of() : draft.questions()) {
            if (added.size() >= request.count()) {
                break;
            }
            try {
                String figure = d.chart() == null ? null : chart(d.chart());
                QuestionRequest q = assessmentService.normalise(new QuestionRequest(
                        AssessmentQuestion.Kind.valueOf(String.valueOf(d.kind()).strip().toUpperCase(Locale.ROOT)), d.prompt(), d.code(),
                        d.options(), d.correctOptions(), d.acceptedAnswers(),
                        d.points() == null ? 1 : Math.max(1, Math.min(10, d.points())), d.explanation(), figure));
                BankQuestion saved = bankRepository.save(apply(BankQuestion.builder()
                        .area(area).section(request.section()).topic(request.topic().strip())
                        .difficulty(request.difficulty()).source(BankQuestion.Source.AI).status(BankQuestion.Status.REVIEW)
                        .createdBy(actor.getEmail()).build(), q));
                added.add(view(saved));
            } catch (IllegalArgumentException | NullPointerException e) {
                notes.add("Skipped a drafted question that wasn't complete.");
            }
        }
        auditService.record(actor, AuditAction.ASSESSMENT_DRAFTED, "BankQuestion", null, Map.of("added", added.size()));
        return new BankDraftResult(added.size(), notes, added);
    }

    /** Draws an AI chart spec; throws IllegalArgumentException when the data doesn't fit. */
    static String chart(AssessmentDraft.Chart c) {
        String type = String.valueOf(c.type()).toUpperCase(Locale.ROOT);
        String title = StringUtils.hasText(c.title()) ? c.title() : "";
        if (type.equals("TABLE")) {
            if (c.headers() == null || c.rows() == null || c.headers().size() < 2 || c.headers().size() > 6 || c.rows().isEmpty()
                    || c.rows().size() > 10 || c.rows().stream().anyMatch(r -> r == null || r.size() != c.headers().size())) {
                throw new IllegalArgumentException("table");
            }
            return Svg.table(title, c.headers(), c.rows());
        }
        if (c.labels() == null || c.values() == null || c.labels().size() != c.values().size() || c.labels().size() < 2
                || c.labels().size() > 8 || c.values().stream().anyMatch(v -> v == null || v < 0)) {
            throw new IllegalArgumentException("chart");
        }
        return switch (type) {
            case "BAR" -> Svg.barChart(title, c.labels(), c.values(), c.unit());
            case "LINE" -> Svg.lineChart(title, c.labels(), c.values(), c.unit());
            case "PIE" -> {
                if (c.values().stream().mapToInt(Integer::intValue).sum() != 100) {
                    throw new IllegalArgumentException("pie");
                }
                yield Svg.pieChart(title, c.labels(), c.values());
            }
            default -> throw new IllegalArgumentException("chart type");
        };
    }

    /** Role tests: every role with its paper at each level. */
    @Transactional(readOnly = true)
    public List<com.codewalnut.ats.dto.BankDtos.RoleView> roles(AppUser actor) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        return com.codewalnut.ats.bank.Roles.ROLES.stream()
                .map(r -> new com.codewalnut.ats.dto.BankDtos.RoleView(r.id(), r.name(), r.summary(), r.primary(),
                        r.areas().stream().map(Presets::areaName).toList(),
                        r.levels().stream().map(l -> new com.codewalnut.ats.dto.BankDtos.RoleLevel(l.name(), l.label, l.years,
                                com.codewalnut.ats.bank.Roles.preset(r, l))).toList()))
                .toList();
    }

    // ---- building papers ----

    /**
     * Builds a draft test from the bank: so many easy/medium/hard per section, or per topic when the
     * request names topics, in the chosen order.
     */
    @Transactional
    public AssessmentDetail build(AppUser actor, BuildRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        boolean byTopic = request.topics() != null && !request.topics().isEmpty();
        List<Plan> plans = byTopic
                ? request.topics().stream().map(p -> new Plan(request.area(), p.section(), p.topic(), p.easy(), p.medium(), p.hard())).toList()
                : request.sections() == null ? List.of()
                : request.sections().stream().map(p -> new Plan(p.area() == null ? request.area() : p.area(), p.section(), null, p.easy(),
                        p.medium(), p.hard())).toList();
        plans.forEach(p -> checkSection(p.area(), p.section()));
        // Role tests mix areas (e.g. Java + SQL); their sections are then labelled per area.
        boolean mixed = plans.stream().map(Plan::area).distinct().count() > 1;
        int total = plans.stream().mapToInt(p -> p.easy() + p.medium() + p.hard()).sum();
        if (total == 0) {
            throw new IllegalArgumentException("sections: ask for at least one question");
        }
        if (total > AssessmentService.MAX_QUESTIONS) {
            throw new IllegalArgumentException("sections: a test can have up to " + AssessmentService.MAX_QUESTIONS + " questions");
        }
        List<String> shortages = new ArrayList<>();
        List<List<BankQuestion>> groups = new ArrayList<>();
        Set<UUID> taken = new java.util.HashSet<>();
        for (Plan plan : plans) {
            List<BankQuestion> chosen = new ArrayList<>();
            int[] wanted = {plan.easy(), plan.medium(), plan.hard()};
            BankQuestion.Difficulty[] levels = BankQuestion.Difficulty.values();
            String label = plan.topic() != null ? plan.topic()
                    : mixed ? Presets.areaName(plan.area()) + " · " + plan.section().getLabel() : plan.section().getLabel();
            for (int d = 0; d < 3; d++) {
                if (wanted[d] == 0) {
                    continue;
                }
                List<BankQuestion> pool = new ArrayList<>(plan.topic() == null
                        ? bankRepository.findByAreaAndSectionAndDifficultyAndStatus(plan.area(), plan.section(), levels[d], BankQuestion.Status.ACTIVE)
                        : bankRepository.findByAreaAndSectionAndTopicAndDifficultyAndStatus(plan.area(), plan.section(), plan.topic().strip(),
                                levels[d], BankQuestion.Status.ACTIVE));
                pool.removeIf(b -> taken.contains(b.getId()));
                if (pool.size() < wanted[d]) {
                    shortages.add(label + " · " + levels[d].name().toLowerCase(Locale.ROOT) + ": " + pool.size() + " available, " + wanted[d] + " asked");
                    continue;
                }
                List<BankQuestion> picked = pickSpread(pool, wanted[d]);
                picked.forEach(b -> taken.add(b.getId()));
                chosen.addAll(picked);
            }
            groups.add(chosen);
        }
        if (!shortages.isEmpty()) {
            throw new IllegalArgumentException("Not enough questions in the bank — " + String.join("; ", shortages)
                    + ". Add questions or ask for fewer.");
        }
        List<BankQuestion> paper = order(groups, request.order());
        String description = byTopic
                ? "Built from the question bank by topic: " + plans.stream().map(p -> p.topic().strip() + " " + (p.easy() + p.medium() + p.hard()))
                        .collect(Collectors.joining(", ")) + "."
                : "Built from the question bank: " + plans.stream().map(p -> (mixed ? Presets.areaName(p.area()) + " · " : "")
                        + p.section().getLabel() + " " + (p.easy() + p.medium() + p.hard()))
                        .collect(Collectors.joining(", ")) + ".";
        Assessment a = assessmentRepository.save(Assessment.builder()
                .title(request.title().strip()).category(request.area())
                .description(description.length() > 2000 ? description.substring(0, 1997) + "…" : description)
                .durationMinutes(request.durationMinutes()).passPercent(request.passPercent())
                .status(Assessment.Status.DRAFT).createdBy(actor.getEmail()).build());
        copyInto(a, paper, 0, mixed);
        auditService.record(actor, AuditAction.ASSESSMENT_CREATED, "Assessment", a.getId(),
                Map.of("fromBank", paper.size(), "order", request.order(), "byTopic", byTopic));
        return assessmentService.detail(a);
    }

    /** Adds chosen bank questions to the end of a draft test. */
    @Transactional
    public AssessmentDetail addToTest(AppUser actor, UUID assessmentId, AddFromBankRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Assessment a = assessmentService.editable(assessmentId);
        int existing = (int) questionRepository.countByAssessmentId(assessmentId);
        if (existing + request.questionIds().size() > AssessmentService.MAX_QUESTIONS) {
            throw new IllegalArgumentException("A test can have up to " + AssessmentService.MAX_QUESTIONS + " questions");
        }
        List<BankQuestion> chosen = request.questionIds().stream().distinct().map(this::bank)
                .filter(b -> b.getStatus() == BankQuestion.Status.ACTIVE).toList();
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("questionIds: pick at least one approved question");
        }
        copyInto(a, chosen, existing, chosen.stream().anyMatch(b -> b.getArea() != a.getCategory()));
        assessmentService.touch(a);
        return assessmentService.detail(a);
    }

    // ---- helpers ----

    /** Aptitude uses numerical/logical/verbal; technical areas use the experience bands. */
    private static void checkSection(Assessment.Category area, BankQuestion.Section section) {
        if (!BankQuestion.Section.forArea(area).contains(section)) {
            throw new IllegalArgumentException("section: " + section.getLabel() + " is not a section of this area");
        }
    }

    /** Least-used first, random among equals, so repeated papers vary. */
    private static List<BankQuestion> pickSpread(List<BankQuestion> pool, int n) {
        Collections.shuffle(pool, RANDOM);
        pool.sort(Comparator.comparingInt(BankQuestion::getTimesUsed));
        return new ArrayList<>(pool.subList(0, n));
    }

    private static List<BankQuestion> order(List<List<BankQuestion>> bySection, com.codewalnut.ats.dto.BankDtos.Order order) {
        Comparator<BankQuestion> byLevel = Comparator.comparing(BankQuestion::getDifficulty);
        if (order == com.codewalnut.ats.dto.BankDtos.Order.HARD_FIRST) {
            List<BankQuestion> out = new ArrayList<>();
            bySection.forEach(out::addAll);
            Collections.shuffle(out, RANDOM);
            out.sort(byLevel.reversed());
            return out;
        }
        List<BankQuestion> out = new ArrayList<>();
        switch (order) {
            case BY_SECTION -> bySection.forEach(list -> list.stream().sorted(byLevel).forEach(out::add));
            case EASY_FIRST -> {
                bySection.forEach(out::addAll);
                List<BankQuestion> shuffled = new ArrayList<>(out);
                Collections.shuffle(shuffled, RANDOM); // mix sections within each level
                out.clear();
                shuffled.stream().sorted(byLevel).forEach(out::add);
            }
            case SHUFFLED -> {
                bySection.forEach(out::addAll);
                Collections.shuffle(out, RANDOM);
            }
            default -> throw new IllegalStateException("order " + order);
        }
        return out;
    }

    /** One block of a paper: so many easy/medium/hard from an area's section (and topic, if set). */
    private record Plan(Assessment.Category area, BankQuestion.Section section, String topic, int easy, int medium, int hard) {}

    /** mixed: the paper spans areas, so sections are stored as AREA:SECTION and scored per area. */
    private void copyInto(Assessment a, List<BankQuestion> questions, int startAt, boolean mixed) {
        int position = startAt;
        for (BankQuestion b : questions) {
            questionRepository.save(AssessmentQuestion.builder()
                    .assessmentId(a.getId()).position(++position).kind(b.getKind()).prompt(b.getPrompt()).code(b.getCode())
                    .optionsJson(b.getOptionsJson()).answerJson(b.getAnswerJson()).codingJson(b.getCodingJson())
                    .points(b.getPoints()).explanation(b.getExplanation())
                    .figure(b.getFigure()).optionFiguresJson(b.getOptionFiguresJson())
                    .section(mixed ? b.getArea().name() + ":" + b.getSection().name() : b.getSection().name())
                    .topic(b.getTopic()).difficulty(b.getDifficulty().name()).bankQuestionId(b.getId())
                    .aiDrafted(false).build());
            b.setTimesUsed(b.getTimesUsed() + 1);
        }
    }

    private BankQuestion apply(BankQuestion b, QuestionRequest q) {
        b.setKind(q.kind());
        b.setPrompt(q.prompt());
        b.setCode(q.code());
        b.setFigure(q.figure());
        if (q.kind() == AssessmentQuestion.Kind.CODING) {
            com.codewalnut.ats.service.CodingSpecs.Stored stored = codingSpecs.normalise(q.coding());
            b.setOptionsJson(null);
            b.setCodingJson(stored.specJson());
            b.setAnswerJson(stored.testsJson());
        } else {
            b.setCodingJson(null);
            b.setOptionsJson(q.kind() == AssessmentQuestion.Kind.SHORT_ANSWER ? null : assessmentService.write(q.options()));
            b.setAnswerJson(assessmentService.write(q.kind() == AssessmentQuestion.Kind.SHORT_ANSWER ? q.acceptedAnswers() : q.correct()));
        }
        b.setPoints(q.points());
        b.setExplanation(q.explanation());
        return b;
    }

    private BankQuestionView view(BankQuestion b) {
        List<String> options = b.getOptionsJson() == null ? List.of() : assessmentService.readList(b.getOptionsJson(), new TypeReference<List<String>>() {});
        List<String> optionFigures = b.getOptionFiguresJson() == null ? null
                : assessmentService.readList(b.getOptionFiguresJson(), new TypeReference<List<String>>() {});
        boolean shortAnswer = b.getKind() == AssessmentQuestion.Kind.SHORT_ANSWER;
        boolean coding = b.getKind() == AssessmentQuestion.Kind.CODING;
        return new BankQuestionView(b.getId(), b.getArea(), b.getSection(), b.getSection().getLabel(), b.getTopic(), b.getDifficulty(),
                b.getKind(), b.getPrompt(), b.getCode(), b.getFigure(), options, optionFigures,
                shortAnswer || coding ? List.of() : assessmentService.readList(b.getAnswerJson(), new TypeReference<List<Integer>>() {}),
                shortAnswer ? assessmentService.readList(b.getAnswerJson(), new TypeReference<List<String>>() {}) : List.of(),
                b.getPoints(), b.getExplanation(), b.getSource(), b.getStatus(), b.getTimesUsed(),
                coding ? new com.codewalnut.ats.dto.AssessmentDtos.CodingView(codingSpecs.spec(b.getCodingJson()), codingSpecs.tests(b.getAnswerJson()))
                        : null);
    }

    private BankQuestion bank(UUID id) {
        return bankRepository.findById(id).orElseThrow(() -> new NotFoundException("Question not found"));
    }
}
