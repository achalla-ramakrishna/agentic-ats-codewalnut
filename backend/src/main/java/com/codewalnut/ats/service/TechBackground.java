package com.codewalnut.ats.service;

import com.codewalnut.ats.client.ResumeInsight;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which stack a candidate's résumé points to (ASMT-39), so a Java, Python or MERN test goes to the
 * right people. Keywords in the skills list count double; experience, projects and headline count
 * once. Advisory only: staff still tick who gets each test.
 */
public final class TechBackground {

    public enum Track { JAVA, PYTHON, MERN, MIXED }

    public record Result(Track track, List<String> evidence) {}

    private record Keyword(Track track, String label, Pattern pattern, int weight) {}

    private static final List<Keyword> KEYWORDS = List.of(
            kw(Track.JAVA, "Java", "\\bjava\\b(?!\\s*script)", 2),
            kw(Track.JAVA, "Spring", "\\bspring\\b", 2),
            kw(Track.JAVA, "Hibernate", "\\bhibernate\\b", 2),
            kw(Track.JAVA, "JPA", "\\bjpa\\b", 1),
            kw(Track.JAVA, "Servlets/JSP", "\\b(servlets?|jsp)\\b", 1),
            kw(Track.JAVA, "Maven", "\\bmaven\\b", 1),
            kw(Track.PYTHON, "Python", "\\bpython\\b", 2),
            kw(Track.PYTHON, "Django", "\\bdjango\\b", 2),
            kw(Track.PYTHON, "Flask", "\\bflask\\b", 2),
            kw(Track.PYTHON, "FastAPI", "\\bfast\\s?api\\b", 2),
            kw(Track.PYTHON, "Pandas/NumPy", "\\b(pandas|numpy)\\b", 1),
            kw(Track.MERN, "MERN", "\\bmern\\b", 3),
            kw(Track.MERN, "React", "\\breact(\\.?js)?\\b", 2),
            kw(Track.MERN, "Node.js", "\\bnode(\\.?js)?\\b", 2),
            kw(Track.MERN, "Express", "\\bexpress(\\.?js)?\\b", 2),
            kw(Track.MERN, "MongoDB", "\\bmongo(db)?\\b", 2),
            kw(Track.MERN, "Next.js", "\\bnext\\.?js\\b", 1),
            kw(Track.MERN, "JavaScript/TypeScript", "\\b(java\\s*script|typescript|js|ts)\\b", 1));

    private TechBackground() {}

    private static Keyword kw(Track track, String label, String regex, int weight) {
        return new Keyword(track, label, Pattern.compile(regex, Pattern.CASE_INSENSITIVE), weight);
    }

    /** The track, or null when the résumé shows none of the three stacks. */
    public static Result of(ResumeInsight profile) {
        if (profile == null) {
            return new Result(null, List.of());
        }
        List<String> skills = profile.skills() == null ? List.of() : profile.skills();
        List<String> other = new ArrayList<>();
        other.add(profile.headline());
        other.add(profile.currentRole());
        if (profile.experience() != null) {
            profile.experience().forEach(e -> {
                other.add(e.role());
                other.add(e.summary());
            });
        }
        if (profile.projects() != null) {
            profile.projects().forEach(p -> {
                other.add(p.name());
                other.add(p.summary());
            });
        }
        return of(String.join(" | ", skills), String.join(" | ", other.stream().filter(s -> s != null).toList()));
    }

    static Result of(String skills, String otherText) {
        Map<Track, Integer> score = new LinkedHashMap<>();
        Map<Track, Set<String>> found = new LinkedHashMap<>();
        for (Keyword k : KEYWORDS) {
            int points = (k.pattern().matcher(skills).find() ? 2 * k.weight() : 0)
                    + (k.pattern().matcher(otherText).find() ? k.weight() : 0);
            if (points > 0) {
                score.merge(k.track(), points, Integer::sum);
                found.computeIfAbsent(k.track(), t -> new LinkedHashSet<>()).add(k.label());
            }
        }
        List<Map.Entry<Track, Integer>> ranked = score.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .sorted(Map.Entry.<Track, Integer>comparingByValue().reversed())
                .toList();
        if (ranked.isEmpty()) {
            return new Result(null, List.of());
        }
        Track top = ranked.get(0).getKey();
        boolean clear = ranked.size() == 1 || ranked.get(0).getValue() >= 2 * ranked.get(1).getValue();
        if (clear) {
            return new Result(top, List.copyOf(found.get(top)));
        }
        List<String> evidence = new ArrayList<>();
        ranked.forEach(e -> evidence.addAll(found.get(e.getKey())));
        return new Result(Track.MIXED, evidence);
    }
}
