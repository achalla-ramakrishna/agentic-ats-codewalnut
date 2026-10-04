package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.dto.AssessmentDtos.CodingSpec;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
import com.codewalnut.ats.client.CodeRunner;
import com.codewalnut.ats.service.CodingSpecs;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * CodeWalnut's built-in coding problems (ADR-0016), one YAML file each in
 * {@code resources/bank/coding/}. A problem reads stdin and prints its answer; samples are shown
 * to candidates and hidden tests are not. Keys hash the file's content, so an edited problem
 * replaces the old one on load and never changes a test already built from it.
 */
public final class CodingBank {

    /** Topics in catalogue order: basics for freshers, core DSA for 1–3 years, harder DSA for 3+. */
    public static final List<TechBank.Topic> TOPICS = List.of(
            topic("code-basics", Section.FUNDAMENTALS, "Basic programming", "Loops, conditions, arithmetic, digits and simple simulations.",
                    "Sum of digits"),
            topic("code-strings", Section.FUNDAMENTALS, "Strings", "Reversing, counting characters, palindromes, words and simple parsing.",
                    "Palindrome check"),
            topic("code-arrays", Section.FUNDAMENTALS, "Arrays", "Traversals, prefix sums, min/max, rotations and in-place updates.",
                    "Second largest"),
            topic("code-hashing", Section.PRACTICAL, "Hashing", "Frequency counts, sets and maps, pairs with a target, grouping.", "Two sum"),
            topic("code-two-pointers", Section.PRACTICAL, "Two pointers and sliding window",
                    "Sorted-array pairs, windows with a condition, longest substring problems.", "Longest substring without repeats"),
            topic("code-stacks", Section.PRACTICAL, "Stacks and queues", "Bracket matching, next greater element, simulation with queues.",
                    "Balanced brackets"),
            topic("code-sorting", Section.PRACTICAL, "Sorting and searching", "Binary search, custom sorting, merging intervals, counting.",
                    "Binary search for the first position"),
            topic("code-recursion", Section.PRACTICAL, "Recursion and backtracking", "Subsets, permutations, combinations and grid search.",
                    "Generate subsets"),
            topic("code-trees-graphs", Section.ADVANCED, "Trees and graphs", "BFS and DFS, connected components, shortest paths, topological order.",
                    "Number of islands"),
            topic("code-dp", Section.ADVANCED, "Dynamic programming", "Climbing stairs, knapsack, longest common subsequence, coin change.",
                    "Coin change"),
            topic("code-greedy", Section.ADVANCED, "Greedy and intervals", "Scheduling, interval merging, choosing the locally best step.",
                    "Meeting rooms"),
            topic("code-heaps", Section.ADVANCED, "Heaps and advanced structures", "Top-k, running median, priority queues, union-find.",
                    "Kth largest"));

    /** A built-in problem as parsed, before it becomes a bank question. */
    public record Problem(String id, String topicId, String title, Difficulty difficulty, String statement, CodingSpec spec,
            List<TestCase> tests, String source) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static List<Problem> cache;

    private CodingBank() {}

    public static List<TechBank.Topic> topics() {
        return TOPICS;
    }

    public static synchronized List<Problem> problems() {
        if (cache == null) {
            List<Problem> out = new ArrayList<>();
            try {
                Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:bank/coding/*.yml");
                for (Resource r : files) {
                    try (InputStream in = r.getInputStream()) {
                        out.add(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), r.getFilename()));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            out.sort(Comparator.comparing(Problem::topicId).thenComparing(Problem::difficulty).thenComparing(Problem::id));
            cache = List.copyOf(out);
        }
        return cache;
    }

    /** Every problem as a bank seed. Points: 5 easy, 10 medium, 15 hard. */
    public static List<Seed> all() {
        List<Seed> seeds = new ArrayList<>();
        for (Problem p : problems()) {
            TechBank.Topic topic = TOPICS.stream().filter(t -> t.id().equals(p.topicId())).findFirst().orElseThrow();
            seeds.add(new Seed("coding:" + p.id() + ":" + hash(p.source()), Category.CODING, topic.section(), topic.name(), p.difficulty(),
                    AssessmentQuestion.Kind.CODING, p.title() + "\n\n" + p.statement(), null, null, List.of(), null, List.of(), List.of(),
                    "Graded by running the code against " + p.tests().size() + " test cases.", json(p.spec()), json(p.tests())));
        }
        return seeds;
    }

    @SuppressWarnings("unchecked")
    public static Problem parse(String yaml, String source) {
        Map<String, Object> m;
        try {
            m = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (RuntimeException e) {
            throw new IllegalStateException(source + ": not valid YAML: " + e.getMessage(), e);
        }
        String id = req(m, "id", source);
        String topicId = req(m, "topic", source);
        if (TOPICS.stream().noneMatch(t -> t.id().equals(topicId))) {
            throw new IllegalStateException(source + ": unknown topic " + topicId);
        }
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(req(m, "difficulty", source));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(source + ": difficulty must be EASY, MEDIUM or HARD");
        }
        List<TestCase> samples = cases(m.get("samples"), source, "samples");
        List<TestCase> tests = cases(m.get("tests"), source, "tests");
        if (samples.isEmpty() || tests.size() < 5) {
            throw new IllegalStateException(source + ": needs at least one sample and five hidden tests");
        }
        Map<String, String> starter = new LinkedHashMap<>();
        Map<String, Object> given = m.get("starter") instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of();
        for (String language : CodeRunner.LANGUAGES) {
            Object code = given.get(language);
            starter.put(language, code == null ? CodingSpecs.defaultStarter(language) : code.toString());
        }
        double seconds = m.get("timeLimitSeconds") instanceof Number n ? n.doubleValue() : 2;
        CodingSpec spec = new CodingSpec(CodeRunner.LANGUAGES, starter, samples, seconds, 256, text(m, "input"), text(m, "output"),
                text(m, "constraints"));
        return new Problem(id, topicId, req(m, "title", source), difficulty, req(m, "statement", source).strip(), spec, tests, yaml);
    }

    @SuppressWarnings("unchecked")
    private static List<TestCase> cases(Object value, String source, String field) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException(source + ": " + field + " must be a list of {input, output}");
        }
        List<TestCase> out = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> c = (Map<String, Object>) o;
            if (c.get("output") == null) {
                throw new IllegalStateException(source + ": every " + field + " entry needs an output");
            }
            out.add(new TestCase(c.get("input") == null ? "" : c.get("input").toString(), c.get("output").toString()));
        }
        return out;
    }

    private static String req(Map<String, Object> m, String key, String source) {
        Object v = m.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalStateException(source + ": missing " + key);
        }
        return v.toString().strip();
    }

    private static String text(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString().strip();
    }

    private static TechBank.Topic topic(String id, Section section, String name, String covers, String example) {
        return new TechBank.Topic(Category.CODING, id, section, name, covers, example);
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
