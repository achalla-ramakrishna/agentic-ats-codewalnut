package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.AssessmentQuestion.Kind;
import com.codewalnut.ats.domain.BankQuestion.Difficulty;
import com.codewalnut.ats.domain.BankQuestion.Section;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * CodeWalnut's built-in technical banks (ADR-0015): Java, Python, JavaScript, React, Angular and
 * SQL, each split into experience bands (fundamentals for freshers, applied for 1–3 years,
 * advanced for 3+ years) and topics. Questions are hand-written in {@code resources/bank/tech/*.txt}:
 *
 * <pre>
 * &#64;topic java-oop | FUNDAMENTALS | OOP basics | Classes, objects, inheritance …
 * Q E What does this print?
 * | System.out.println(1 + 2 + "3");
 * + 33
 * - 123
 * - 6
 * - 15
 * = Left to right: 1 + 2 = 3, then "3" + "3" = "33".
 * </pre>
 *
 * Q takes E, M or H; "|" lines are code; "+" is the one right option, "-" the three others; "="
 * the explanation. A question's key is a hash of its prompt and code, so editing one retires the
 * old version (archived on load) and never changes a question already copied into a test.
 */
public final class TechBank {

    public static final List<Category> AREAS = List.of(Category.JAVA, Category.PYTHON, Category.JAVASCRIPT, Category.REACT,
            Category.ANGULAR, Category.SQL, Category.CS_FUNDAMENTALS, Category.SYSTEM_DESIGN);

    /** A topic: its band, what it covers, and an example (its first question). */
    public record Topic(Category area, String id, Section section, String name, String covers, String example) {}

    private record Parsed(List<Topic> topics, List<Seed> seeds) {}

    private static volatile Map<Category, Parsed> cache;

    private TechBank() {}

    public static List<Topic> topics(Category area) {
        Parsed p = load().get(area);
        return p == null ? List.of() : p.topics();
    }

    public static List<Seed> all() {
        List<Seed> out = new ArrayList<>();
        load().values().forEach(p -> out.addAll(p.seeds()));
        return out;
    }

    private static Map<Category, Parsed> load() {
        Map<Category, Parsed> c = cache;
        if (c == null) {
            c = new EnumMap<>(Category.class);
            for (Category area : AREAS) {
                String name = "/bank/tech/" + area.name().toLowerCase() + ".txt";
                try (InputStream in = TechBank.class.getResourceAsStream(name)) {
                    if (in == null) {
                        throw new IllegalStateException("Missing " + name);
                    }
                    c.put(area, parse(area, new String(in.readAllBytes(), StandardCharsets.UTF_8), name));
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read " + name, e);
                }
            }
            cache = c;
        }
        return c;
    }

    /** Parses one area's file; any mistake fails loudly with the line number. */
    static Parsed parse(Category area, String text, String source) {
        List<Topic> topics = new ArrayList<>();
        List<Seed> seeds = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        String[] lines = text.split("\r?\n");
        String[] topic = null;
        Builder q = null;
        for (int i = 0; i <= lines.length; i++) {
            String line = i < lines.length ? lines[i] : "@end";
            String where = source + ":" + (i + 1);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("@") || line.startsWith("Q ")) {
                if (q != null) {
                    Seed s = q.build(area, topic, where);
                    if (!keys.add(s.key())) {
                        throw new IllegalStateException(where + ": duplicate question in " + topic[2] + ": " + s.prompt());
                    }
                    seeds.add(s);
                    if (topics.get(topics.size() - 1).example() == null) {
                        Topic t = topics.remove(topics.size() - 1);
                        topics.add(new Topic(t.area(), t.id(), t.section(), t.name(), t.covers(), s.prompt()));
                    }
                    q = null;
                }
                if (line.startsWith("@topic ")) {
                    topic = line.substring(7).split("\\s*\\|\\s*", 4);
                    if (topic.length != 4) {
                        throw new IllegalStateException(where + ": @topic needs id | SECTION | name | covers");
                    }
                    Section section = Section.valueOf(topic[1].strip());
                    if (section.isAptitude()) {
                        throw new IllegalStateException(where + ": technical topics use FUNDAMENTALS, PRACTICAL or ADVANCED");
                    }
                    topics.add(new Topic(area, topic[0].strip(), section, topic[2].strip(), topic[3].strip(), null));
                } else if (line.startsWith("Q ")) {
                    if (topic == null) {
                        throw new IllegalStateException(where + ": question before any @topic");
                    }
                    Difficulty d = switch (line.charAt(2)) {
                        case 'E' -> Difficulty.EASY;
                        case 'M' -> Difficulty.MEDIUM;
                        case 'H' -> Difficulty.HARD;
                        default -> throw new IllegalStateException(where + ": Q needs E, M or H");
                    };
                    q = new Builder(d, line.substring(4).strip());
                }
                continue;
            }
            if (q == null) {
                throw new IllegalStateException(where + ": line outside a question: " + line);
            }
            if (line.startsWith("|")) {
                q.code.add(line.length() > 2 ? line.substring(2) : "");
            } else if (line.startsWith("+ ")) {
                q.right = line.substring(2).strip();
                q.options.add(q.right);
            } else if (line.startsWith("- ")) {
                q.options.add(line.substring(2).strip());
            } else if (line.startsWith("= ")) {
                q.explanation = line.substring(2).strip();
            } else {
                throw new IllegalStateException(where + ": unexpected line: " + line);
            }
        }
        return new Parsed(List.copyOf(topics), List.copyOf(seeds));
    }

    private static final class Builder {
        final Difficulty difficulty;
        final String prompt;
        final List<String> code = new ArrayList<>();
        final List<String> options = new ArrayList<>();
        String right;
        String explanation;

        Builder(Difficulty difficulty, String prompt) {
            this.difficulty = difficulty;
            this.prompt = prompt;
        }

        Seed build(Category area, String[] topic, String where) {
            if (right == null || options.size() != 4 || new HashSet<>(options).size() != 4) {
                throw new IllegalStateException(where + ": needs one + and three - options, all different: " + prompt);
            }
            if (explanation == null || explanation.isBlank()) {
                throw new IllegalStateException(where + ": needs an = explanation: " + prompt);
            }
            String snippet = code.isEmpty() ? null : String.join("\n", code);
            String key = "tech:" + area.name().toLowerCase() + ":" + topic[0].strip() + ":" + hash(prompt + "\n" + snippet);
            List<String> shuffled = new ArrayList<>(options);
            Collections.shuffle(shuffled, new Random(key.hashCode()));
            Section section = Section.valueOf(topic[1].strip());
            return new Seed(key, area, section, topic[2].strip(), difficulty, Kind.SINGLE_CHOICE, prompt, snippet, null, shuffled, null,
                    List.of(shuffled.indexOf(right)), List.of(), explanation);
        }
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
