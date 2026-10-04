package com.codewalnut.ats.service;

import com.codewalnut.ats.bank.CodingBank;
import com.codewalnut.ats.bank.Presets;
import com.codewalnut.ats.bank.Roles;
import com.codewalnut.ats.bank.TechBank;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.domain.InterviewKit;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.dto.FeedbackDtos.Competency;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewCategory;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewGuide;
import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewQuestion;
import com.codewalnut.ats.dto.InterviewKitDtos.GenerateKitRequest;
import com.codewalnut.ats.dto.InterviewKitDtos.KitCoding;
import com.codewalnut.ats.dto.InterviewKitDtos.KitContent;
import com.codewalnut.ats.dto.InterviewKitDtos.KitPage;
import com.codewalnut.ats.dto.InterviewKitDtos.KitQuestion;
import com.codewalnut.ats.dto.InterviewKitDtos.KitRound;
import com.codewalnut.ats.dto.InterviewKitDtos.KitSkill;
import com.codewalnut.ats.dto.InterviewKitDtos.KitTest;
import com.codewalnut.ats.dto.InterviewKitDtos.KitView;
import com.codewalnut.ats.dto.InterviewKitDtos.Option;
import com.codewalnut.ats.dto.InterviewKitDtos.ScoreRow;
import com.codewalnut.ats.repository.InterviewKitRepository;
import com.codewalnut.ats.repository.InterviewRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Interview kits (INT-28…, ADR-0019): reads an opening's job description and puts together what
 * the panel needs to assess a candidate for it: the skills it asks for and the level, the matching
 * online test, a plan of interview rounds with questions (from the interview guide and written from
 * the job's must-haves), live coding problems and a scorecard. Rule-based and repeatable, no AI.
 * It suggests; the panel decides what to ask. Staff only: candidates and clients never see it.
 */
@Service
public class InterviewKitService {

    /** A skill to look for: how it's written, its display name, its bank area and interview guide category. */
    record SkillRule(Pattern pattern, String name, Category area, String guide) {}

    private static final List<SkillRule> SKILLS = List.of(
            rule("java(?!\\s*script)", "Java", Category.JAVA, "java"),
            rule("spring(\\s*boot)?", "Spring Boot", Category.JAVA, "java"),
            rule("hibernate|jpa", "Hibernate / JPA", Category.JAVA, "java"),
            rule("python", "Python", Category.PYTHON, "python"),
            rule("django|flask|fastapi", "Django / Flask / FastAPI", Category.PYTHON, "python"),
            rule("javascript|es6|ecmascript", "JavaScript", Category.JAVASCRIPT, "javascript"),
            rule("typescript", "TypeScript", Category.JAVASCRIPT, "javascript"),
            rule("html5?|css3?|tailwind|bootstrap|sass", "HTML & CSS", Category.JAVASCRIPT, "javascript"),
            caseSensitive("React(\\.?js)?|Next\\.?js|Redux", "React", Category.REACT, "react"),
            rule("angular|rxjs", "Angular", Category.ANGULAR, "angular"),
            rule("node(\\.?js)?|express(\\.js)?|nest\\.?js", "Node.js", Category.NODEJS, "nodejs"),
            rule("sql|mysql|postgres(ql)?|oracle|sql server|rdbms", "SQL databases", Category.SQL, "sql"),
            rule("mongodb|mongo|redis|cassandra|dynamodb|nosql", "NoSQL (MongoDB, Redis)", null, "sql"),
            rule("docker|containers?", "Docker", Category.DEVOPS, "devops-cloud"),
            rule("kubernetes|k8s|helm", "Kubernetes", Category.DEVOPS, "devops-cloud"),
            rule("aws|amazon web services|azure|gcp|google cloud", "Cloud (AWS, Azure, GCP)", Category.DEVOPS, "devops-cloud"),
            rule("ci\\s*/\\s*cd|jenkins|github actions|gitlab ci|terraform|ansible", "CI/CD and infrastructure", Category.DEVOPS, "devops-cloud"),
            rule("selenium|playwright|cypress|appium|test automation|automation testing|qa\\b|quality assurance|api testing",
                    "Test automation", Category.QA_AUTOMATION, "qa-automation"),
            caseSensitive("Excel|Power ?BI|Tableau|Looker", "Data analysis and BI", Category.DATA_ANALYTICS, "data-analytics"),
            rule("data analy(sis|tics)|dashboards?|statistics|power ?bi|tableau", "Data analysis and BI", Category.DATA_ANALYTICS,
                    "data-analytics"),
            rule("micro-?services|system design|distributed systems?|scalab(le|ility)|high availability|kafka|rabbitmq",
                    "System design and microservices", Category.SYSTEM_DESIGN, "system-design"),
            rule("data structures?|algorithms?|dsa|problem[- ]solving", "Data structures and algorithms", Category.DSA, "dsa-freshers"),
            rule("llms?|gen ?ai|generative ai|machine learning|\\bml\\b|rag|prompt engineering|langchain|openai|nlp|deep learning",
                    "AI / GenAI", null, "ai-genai"),
            rule("restful|rest\\s*apis?|graphql|apis?", "APIs (REST)", null, null),
            rule("git|github|gitlab|bitbucket", "Git", null, null));

    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*(?:\\+|plus)?\\s*(?:(?:-|–|to)\\s*(\\d{1,2}))?\\s*\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FRESHER = Pattern.compile("\\b(interns?|internship|freshers?|graduates?|trainees?|entry[- ]level|campus)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAD = Pattern.compile("\\b(lead|architect|principal|staff engineer|head of|manager)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENIOR = Pattern.compile("\\b(senior|sr\\.?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JUNIOR = Pattern.compile("\\b(junior|jr\\.?|associate)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUST = Pattern.compile("must|required|requirement|mandatory|strong|proficien|expert|hands[- ]on|solid|essential",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NICE = Pattern.compile("nice to have|good to have|preferred|a plus|bonus|optional|exposure to|familiarity",
            Pattern.CASE_INSENSITIVE);

    /** Guide categories asked about in their own rounds, not in the technical round. */
    private static final Set<String> OWN_ROUND = Set.of("project-behaviour", "problem-solving", "live-coding", "system-design", "dsa-freshers");

    private static final String LOOK_FOR = "Asks about the input and edge cases first, explains the approach and its cost before coding, "
            + "writes working code, then tests it with the sample and an edge case. Hints are fine; note how many were needed.";

    private final JobOpeningRepository jobRepository;
    private final InterviewKitRepository kitRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewGuideService guideService;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public InterviewKitService(JobOpeningRepository jobRepository, InterviewKitRepository kitRepository,
            InterviewRepository interviewRepository, InterviewGuideService guideService, AccessPolicy accessPolicy,
            AuditService auditService, ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.kitRepository = kitRepository;
        this.interviewRepository = interviewRepository;
        this.guideService = guideService;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public KitPage page(AppUser actor, UUID jobId) {
        JobOpening job = visibleJob(actor, jobId);
        KitView view = kitRepository.findByJobId(jobId).map(k -> view(job, k)).orElse(null);
        return new KitPage(job.getId(), job.getTitle(), clientName(job), StringUtils.hasText(job.getDescription()), view,
                accessPolicy.has(actor, Capability.MANAGE_JOBS),
                Arrays.stream(Roles.Level.values()).map(l -> new Option(l.name(), l.label + " (" + l.years + ")")).toList(),
                Roles.ROLES.stream().map(r -> new Option(r.id(), r.name())).toList());
    }

    /** Generates (or regenerates, with fresh question picks) the kit for an opening. */
    @Transactional
    public KitPage generate(AppUser actor, UUID jobId, GenerateKitRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Opening not found"));
        Roles.Level level = null;
        if (request != null && StringUtils.hasText(request.level())) {
            try {
                level = Roles.Level.valueOf(request.level().strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("level: unknown level " + request.level());
            }
        }
        Roles.Role role = request != null && StringUtils.hasText(request.roleId()) ? Roles.role(request.roleId().strip()) : null;
        long seed = new Random().nextLong();
        KitContent content = build(job.getTitle(), job.getDescription(), job.getWorkMode() == null ? null : job.getWorkMode().name(),
                job.getLocation(), clientName(job), level, role, seed);
        InterviewKit kit = kitRepository.findByJobId(jobId).orElseGet(() -> InterviewKit.builder().jobId(jobId).build());
        kit.setKitJson(write(content));
        kit.setSourceHash(hash(job.getTitle(), job.getDescription()));
        kit.setGeneratedBy(actor.getEmail());
        kit.setGeneratedAt(Instant.now());
        kitRepository.save(kit);
        auditService.record(actor, AuditAction.INTERVIEW_KIT_GENERATED, "JobOpening", jobId,
                Map.of("role", content.roleId(), "level", content.level()));
        return page(actor, jobId);
    }

    /** The job-specific rows of an opening's scorecard, for the feedback form; empty without a kit. */
    @Transactional(readOnly = true)
    public List<Competency> extraCompetencies(UUID jobId) {
        return kitRepository.findByJobId(jobId).map(k -> {
            KitContent c = read(k.getKitJson());
            return c.scorecard().stream().filter(r -> InterviewFeedbackService.COMPETENCIES.stream().noneMatch(x -> x.name().equals(r.name())))
                    .map(r -> new Competency(r.name(), r.guidance())).toList();
        }).orElse(List.of());
    }

    // ---- building a kit ----

    /** Pure function of the job's text, the overrides and the seed; package-private for tests. */
    KitContent build(String title, String description, String workMode, String location, String client, Roles.Level levelOverride,
            Roles.Role roleOverride, long seed) {
        String text = (title == null ? "" : title) + "\n" + (description == null ? "" : description);
        List<String> notes = new ArrayList<>();
        if (!StringUtils.hasText(description)) {
            notes.add("The opening has no job description, so this kit is based on the title only. Add a description and regenerate for a better kit.");
        }
        List<KitSkill> skills = skills(title, description);
        Roles.Level detected = detectLevel(title, text, notes);
        Roles.Level level = levelOverride != null ? levelOverride : detected;
        Roles.Role detectedRole = matchRole(skills, level, notes);
        Roles.Role role = roleOverride != null ? roleOverride : detectedRole;
        if (!role.levels().contains(level)) {
            Roles.Level fitted = role.levels().get(0);
            notes.add(role.name() + " tests are only for " + fitted.label + "; the test uses that level.");
        }
        Roles.Level testLevel = role.levels().contains(level) ? level : role.levels().get(0);
        if (skills.isEmpty()) {
            notes.add("No known skills were found in the job description; questions come from the role's usual topics.");
        }
        Random random = new Random(seed);
        InterviewGuide guide = guideService.content();
        String g = guideLevel(level);
        List<KitSkill> mustHaves = skills.stream().filter(KitSkill::mustHave).limit(4).toList();
        boolean fresher = level == Roles.Level.FRESHER;

        List<KitRound> rounds = new ArrayList<>();
        rounds.add(screening(mustHaves, workMode, location, client, fresher));
        rounds.add(technical(skills, mustHaves, role, guide, g, fresher, random));
        rounds.add(coding(role, level, guide, g, random));
        if (level.ordinal() >= Roles.Level.MID.ordinal()) {
            rounds.add(new KitRound("System design", 45, "A senior engineer",
                    "Can they design something real for this role's scale, and reason about trade-offs?",
                    pick(guide, "system-design", g, 2, random), List.of()));
        }
        rounds.add(new KitRound(fresher ? "Projects and attitude" : "Project deep-dive and fit", 20, "Hiring manager",
                fresher ? "What have they built on their own, how do they learn, and how do they work with others?"
                        : "Did they really do what their résumé says? Ownership, judgement and how they work in a team.",
                pick(guide, "project-behaviour", g, 3, random), List.of()));

        List<ScoreRow> scorecard = new ArrayList<>(InterviewFeedbackService.COMPETENCIES.stream()
                .map(c -> new ScoreRow(c.name(), c.guidance())).toList());
        mustHaves.forEach(s -> scorecard.add(new ScoreRow(s.name(), "Hands-on depth in " + s.name() + ", as this job needs.")));
        String hireBar = "Score each question 1–4 straight after the answer, with one line of evidence. Hire bar: an average of 3 or more on the "
                + "core questions, nothing below 2 on the job's must-haves (" + (mustHaves.isEmpty() ? "the role's core skills"
                : String.join(", ", mustHaves.stream().map(KitSkill::name).toList())) + "), and a clear yes on the project deep-dive. "
                + "The score guides the panel; the panel decides.";

        var preset = Roles.preset(role, testLevel);
        List<String> areas = new ArrayList<>();
        preset.sections().stream().map(s -> Presets.areaName(s.area())).distinct().forEach(areas::add);
        KitTest test = new KitTest(role.id(), role.name(), testLevel.name(), testLevel.label, testLevel.years, areas, preset);
        return new KitContent(level.name(), level.label + " (" + level.years + ")", detected.name(), role.id(), role.name(),
                detectedRole.id(), skills, notes, test, rounds, scorecard, hireBar, seed);
    }

    static List<KitSkill> skills(String title, String description) {
        String t = title == null ? "" : title;
        String d = description == null ? "" : description;
        Map<String, int[]> counts = new LinkedHashMap<>(); // name → {mentions, must, nice, inTitle}
        Map<String, Category> areas = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>(List.of(t));
        lines.addAll(Arrays.asList(d.split("\\r?\\n")));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean nice = NICE.matcher(line).find();
            boolean must = i == 0 || MUST.matcher(line).find();
            for (SkillRule r : SKILLS) {
                Matcher m = r.pattern().matcher(line);
                int n = 0;
                while (m.find()) {
                    n++;
                }
                if (n == 0) {
                    continue;
                }
                int[] c = counts.computeIfAbsent(r.name(), k -> new int[4]);
                areas.putIfAbsent(r.name(), r.area());
                c[0] += n;
                if (must) {
                    c[1]++;
                }
                if (nice && !must) {
                    c[2]++;
                } else {
                    c[3]++;
                }
            }
        }
        List<KitSkill> out = new ArrayList<>();
        counts.forEach((name, c) -> out.add(new KitSkill(name, areas.get(name) == null ? null : Presets.areaName(areas.get(name)), c[0],
                c[3] > 0)));
        out.sort((a, b) -> a.mustHave() != b.mustHave() ? (a.mustHave() ? -1 : 1) : Integer.compare(b.mentions(), a.mentions()));
        return out;
    }

    static Roles.Level detectLevel(String title, String text, List<String> notes) {
        Integer years = null;
        Matcher m = YEARS.matcher(text);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y <= 25 && (years == null || y < years)) {
                years = y;
            }
        }
        String t = title == null ? "" : title;
        if (FRESHER.matcher(t).find()) {
            return Roles.Level.FRESHER;
        }
        if (years != null) {
            return years < 1 ? Roles.Level.FRESHER : years < 3 ? Roles.Level.JUNIOR : years < 5 ? Roles.Level.MID
                    : years < 8 ? Roles.Level.SENIOR : Roles.Level.LEAD;
        }
        if (LEAD.matcher(t).find()) {
            return Roles.Level.LEAD;
        }
        if (SENIOR.matcher(t).find()) {
            return Roles.Level.SENIOR;
        }
        if (JUNIOR.matcher(t).find()) {
            return Roles.Level.JUNIOR;
        }
        if (FRESHER.matcher(text).find()) {
            return Roles.Level.FRESHER;
        }
        notes.add("The job description doesn't say how many years of experience it needs, so the kit assumes 1–3 years. Change the level if that's wrong.");
        return Roles.Level.JUNIOR;
    }

    static Roles.Role matchRole(List<KitSkill> skills, Roles.Level level, List<String> notes) {
        Set<Category> found = new LinkedHashSet<>();
        for (KitSkill s : skills) {
            SKILLS.stream().filter(r -> r.name().equals(s.name()) && r.area() != null).forEach(r -> found.add(r.area()));
        }
        Roles.Role best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Roles.Role r : Roles.ROLES) {
            if (r.id().equals("graduate-trainee")) {
                continue;
            }
            int score = found.contains(r.primary()) ? 3 : -2;
            for (Category o : r.others()) {
                score += found.contains(o) ? 1 : -1;
            }
            if (score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        if (bestScore < 1) {
            if (level == Roles.Level.FRESHER) {
                return Roles.role("graduate-trainee");
            }
            notes.add("The job description doesn't clearly match one of the role tests; the kit uses " + best.name() + ". Pick another role if that's wrong.");
        }
        return best;
    }

    private static KitRound screening(List<KitSkill> mustHaves, String workMode, String location, String client, boolean fresher) {
        List<KitQuestion> qs = new ArrayList<>();
        String company = client == null ? "CodeWalnut" : client + " (through CodeWalnut)";
        qs.add(new KitQuestion("Motivation", "Walk me through your background in two minutes, and tell me why this role at " + company + " interests you.",
                "A short, clear story that connects their experience to this role; knows something about the work.",
                "Rambling, no clear reason for applying, or knows nothing about the role.", "job"));
        if (!mustHaves.isEmpty()) {
            String list = String.join(", ", mustHaves.stream().map(KitSkill::name).toList());
            qs.add(new KitQuestion("Must-haves", "The job needs " + list + ". Which of these have you used " + (fresher ? "in projects" : "at work")
                    + ", for how long, and how would you rate yourself from 1 to 5 on each?",
                    "Honest, specific answers with time spent and an example for each; ratings that match the examples.",
                    "Rates everything 5 but can't give an example, or hasn't used the main must-have at all.", "job"));
        }
        if (StringUtils.hasText(workMode) || StringUtils.hasText(location)) {
            String where = (StringUtils.hasText(workMode) ? workMode.toLowerCase(Locale.ROOT).replace('_', ' ') : "")
                    + (StringUtils.hasText(location) ? (StringUtils.hasText(workMode) ? " in " : "in ") + location : "");
            qs.add(new KitQuestion("Work mode", "This role is " + where.strip() + ". Does that work for you?",
                    "A clear yes, or a clear and workable condition.", "Unsure, or expects a different arrangement.", "job"));
        }
        qs.add(new KitQuestion("Availability", fresher ? "When can you join, and are you available full time for the whole internship or training?"
                : "What is your notice period, and your current and expected compensation?",
                "Clear dates and numbers within the range for the role.", "Vague, or far outside the range.", "job"));
        return new KitRound("Screening call", 15, "Recruiter", "Basics before spending engineers' time: interest, must-haves, availability.", qs,
                List.of());
    }

    private KitRound technical(List<KitSkill> skills, List<KitSkill> mustHaves, Roles.Role role, InterviewGuide guide, String g,
            boolean fresher, Random random) {
        List<KitQuestion> qs = new ArrayList<>();
        for (KitSkill s : mustHaves.stream().limit(3).toList()) {
            qs.add(new KitQuestion(s.name(), fresher
                    ? "Tell me about a college or personal project where you used " + s.name() + ". What did you build yourself, what was hard, and what would you do differently now?"
                    : "Walk me through something you built with " + s.name() + ": your part, one hard problem and how you solved it, and what you would change now.",
                    "A specific project, their own contribution, concrete technical detail about " + s.name() + ", trade-offs and a lesson learned.",
                    "Only lists features or says \"we\"; can't explain how " + s.name() + " was used or why.", "job"));
        }
        // Guide questions for the job's skills; the role's usual topics when the description names none.
        List<String> categories = new ArrayList<>();
        for (KitSkill s : skills) {
            SKILLS.stream().filter(r -> r.name().equals(s.name()) && r.guide() != null && !OWN_ROUND.contains(r.guide()))
                    .map(SkillRule::guide).filter(c -> !categories.contains(c)).forEach(categories::add);
        }
        if (categories.isEmpty()) {
            guide.roles().stream().filter(r -> r.id().equals(role.id())).findFirst()
                    .ifPresent(r -> r.categories().stream().filter(c -> !OWN_ROUND.contains(c)).forEach(categories::add));
        }
        for (String c : categories.stream().limit(3).toList()) {
            qs.addAll(pick(guide, c, g, categories.size() == 1 ? 4 : 3, random));
        }
        return new KitRound("Technical interview", 60, "An engineer who knows the stack",
                "Depth in the job's must-haves: can they explain how and why, not just what? Ask follow-ups.", qs, List.of());
    }

    private KitRound coding(Roles.Role role, Roles.Level level, InterviewGuide guide, String g, Random random) {
        if (Set.of("sql-developer", "devops-engineer", "data-analyst").contains(role.id())) {
            String c = switch (role.id()) {
                case "sql-developer" -> "sql";
                case "devops-engineer" -> "devops-cloud";
                default -> "data-analytics";
            };
            return new KitRound("Hands-on exercise", 30, "An engineer",
                    "Watch them work through practical tasks for this role, thinking aloud.", pick(guide, c, g, 3, random), List.of());
        }
        List<KitCoding> problems = new ArrayList<>();
        switch (level) {
            case FRESHER -> {
                addProblem(problems, Set.of(Section.FUNDAMENTALS), List.of(Difficulty.EASY), random);
                addProblem(problems, Set.of(Section.FUNDAMENTALS, Section.PRACTICAL), List.of(Difficulty.EASY, Difficulty.MEDIUM), random);
            }
            case JUNIOR -> {
                addProblem(problems, Set.of(Section.FUNDAMENTALS), List.of(Difficulty.EASY, Difficulty.MEDIUM), random);
                addProblem(problems, Set.of(Section.PRACTICAL), List.of(Difficulty.MEDIUM, Difficulty.EASY), random);
            }
            case MID -> {
                addProblem(problems, Set.of(Section.PRACTICAL), List.of(Difficulty.MEDIUM), random);
                addProblem(problems, Set.of(Section.ADVANCED), List.of(Difficulty.MEDIUM, Difficulty.EASY), random);
            }
            default -> {
                addProblem(problems, Set.of(Section.PRACTICAL), List.of(Difficulty.MEDIUM), random);
                addProblem(problems, Set.of(Section.ADVANCED), List.of(Difficulty.HARD, Difficulty.MEDIUM), random);
            }
        }
        // The easier problem is the warm-up.
        problems.sort(java.util.Comparator.comparing(c -> Difficulty.valueOf(c.difficulty())));
        List<KitQuestion> talk = level.ordinal() <= Roles.Level.JUNIOR.ordinal() ? pick(guide, "dsa-freshers", g, 3, random)
                : pick(guide, "live-coding", g, 2, random);
        return new KitRound("Live coding", level == Roles.Level.FRESHER ? 45 : 40, "An engineer",
                "Share an editor; the candidate picks Java, Python, JavaScript or C++. Start with the warm-up, then the main problem. "
                        + "Use the questions to talk about data structures while they code.", talk, problems);
    }

    /** Adds one unused problem from the sections, trying the difficulties in order. */
    private static void addProblem(List<KitCoding> out, Set<Section> sections, List<Difficulty> difficulties, Random random) {
        Map<String, Section> topicSection = new LinkedHashMap<>();
        Map<String, String> topicName = new LinkedHashMap<>();
        for (TechBank.Topic t : CodingBank.topics()) {
            topicSection.put(t.id(), t.section());
            topicName.put(t.id(), t.name());
        }
        Set<String> used = new LinkedHashSet<>();
        out.forEach(c -> used.add(c.id()));
        for (Difficulty d : difficulties) {
            List<CodingBank.Problem> pool = CodingBank.problems().stream()
                    .filter(p -> sections.contains(topicSection.get(p.topicId())) && p.difficulty() == d && !used.contains(p.id())).toList();
            if (!pool.isEmpty()) {
                CodingBank.Problem p = pool.get(random.nextInt(pool.size()));
                var sample = p.spec().samples().isEmpty() ? null : p.spec().samples().get(0);
                out.add(new KitCoding(p.id(), p.title(), d.name(), topicName.get(p.topicId()), p.statement().strip(),
                        p.spec().inputFormat(), p.spec().outputFormat(), sample == null ? null : sample.input(),
                        sample == null ? null : sample.output(), p.spec().constraints(), LOOK_FOR));
                return;
            }
        }
    }

    /** Up to n questions from a guide category at the level, filling from the nearest other levels. */
    static List<KitQuestion> pick(InterviewGuide guide, String categoryId, String level, int n, Random random) {
        InterviewCategory c = guide.categories().stream().filter(x -> x.id().equals(categoryId)).findFirst().orElse(null);
        if (c == null) {
            return List.of();
        }
        List<String> order = switch (level) {
            case "F" -> List.of("F", "J", "S");
            case "J" -> List.of("J", "F", "S");
            default -> List.of("S", "J", "F");
        };
        List<KitQuestion> out = new ArrayList<>();
        for (String l : order) {
            List<InterviewQuestion> pool = new ArrayList<>(c.questions().stream().filter(q -> q.level().equals(l)).toList());
            java.util.Collections.shuffle(pool, random);
            for (InterviewQuestion q : pool) {
                if (out.size() >= n) {
                    return out;
                }
                String topic = q.language() == null ? q.topic() : q.topic() + " (" + q.language() + ")";
                out.add(new KitQuestion(topic, q.question(), q.strong(), q.redFlags(), c.name()));
            }
        }
        return out;
    }

    private static String guideLevel(Roles.Level level) {
        return switch (level) {
            case FRESHER -> "F";
            case JUNIOR -> "J";
            default -> "S";
        };
    }

    // ---- access and storage ----

    /** Hiring staff see any opening's kit; interviewers only for openings where they're on an interview panel. */
    private JobOpening visibleJob(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_INTERVIEWS);
        JobOpening job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Opening not found"));
        if (!accessPolicy.has(actor, Capability.VIEW_CANDIDATES)) {
            String me = actor.getEmail().toLowerCase(Locale.ROOT);
            boolean onPanel = interviewRepository.findByApplicationJobId(jobId).stream()
                    .anyMatch(i -> InterviewFeedbackService.onPanel(i, me));
            if (!onPanel) {
                throw new NotFoundException("Opening not found");
            }
        }
        return job;
    }

    private KitView view(JobOpening job, InterviewKit k) {
        return new KitView(job.getId(), job.getTitle(), clientName(job), read(k.getKitJson()), k.getGeneratedBy(), k.getGeneratedAt(),
                !k.getSourceHash().equals(hash(job.getTitle(), job.getDescription())));
    }

    private static String clientName(JobOpening job) {
        return job.getClient() == null ? null : job.getClient().getName();
    }

    static String hash(String title, String description) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(((title == null ? "" : title) + "\u0000" + (description == null ? "" : description))
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(KitContent c) {
        try {
            return objectMapper.writeValueAsString(c);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private KitContent read(String json) {
        try {
            return objectMapper.readValue(json, KitContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SkillRule rule(String regex, String name, Category area, String guide) {
        return new SkillRule(Pattern.compile("(?<![A-Za-z0-9])(?:" + regex + ")(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE), name, area, guide);
    }

    private static SkillRule caseSensitive(String regex, String name, Category area, String guide) {
        return new SkillRule(Pattern.compile("(?<![A-Za-z0-9])(?:" + regex + ")(?![A-Za-z0-9])"), name, area, guide);
    }
}
