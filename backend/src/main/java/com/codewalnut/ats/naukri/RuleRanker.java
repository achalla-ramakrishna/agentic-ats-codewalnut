package com.codewalnut.ats.naukri;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scores a portal profile against an opening without AI (ADR-0026): skills named in the opening
 * that appear in the headline, key skills or designation (50), experience against the years asked
 * for (25), the job title's words in the designation (15), location (5) and notice period (5).
 * Used when the AI is off or fails, and in the dev profile.
 */
public final class RuleRanker {

    public record Score(int percent, List<String> strengths, List<String> gaps) {}

    /** Skills worth looking for; only those named in the opening count. */
    static final List<String> SKILLS = List.of("java", "j2ee", "spring boot", "spring", "hibernate", "jpa", "microservices",
            "rest", "kafka", "rabbitmq", "sql", "mysql", "postgresql", "oracle", "mongodb", "redis", "aws", "azure", "gcp",
            "docker", "kubernetes", "jenkins", "ci/cd", "git", "junit", "maven", "gradle", "python", "django", "flask",
            "fastapi", "javascript", "typescript", "react", "angular", "vue", "node", "express", "next.js", "html", "css",
            "graphql", "golang", "c#", ".net", "php", "laravel", "selenium", "playwright", "cypress", "android",
            "kotlin", "swift", "flutter", "react native", "terraform", "linux", "data structures", "system design",
            "machine learning", "pandas", "spark", "power bi", "tableau", "figma", "elasticsearch", "camunda", "jms",
            "multithreading", "design patterns", "solid", "oops", "agile");
    private static final Pattern RANGE = Pattern.compile("(\\d{1,2})\\s*(?:-|–|to)\\s*(\\d{1,2})\\s*\\+?\\s*(?:years|yrs|year)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:years|yrs|year)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TITLE_STOP = Set.of("developer", "engineer", "the", "and", "for", "with", "role", "opening", "-", "/");

    private RuleRanker() {}

    /** The skills the opening asks for, in the order they appear in our list. */
    public static List<String> skillsIn(String jobText) {
        String t = " " + jobText.toLowerCase(Locale.ROOT) + " ";
        List<String> found = new ArrayList<>();
        for (String s : SKILLS) {
            if (contains(t, s) && found.stream().noneMatch(f -> f.contains(s))) {
                found.add(s);
            }
        }
        return found;
    }

    /** Years asked for: [min, max]; max is null for "5+ years"; null when not stated. */
    public static int[] yearsIn(String jobText) {
        Matcher r = RANGE.matcher(jobText);
        if (r.find()) {
            return new int[] {Integer.parseInt(r.group(1)), Integer.parseInt(r.group(2))};
        }
        Matcher m = MIN.matcher(jobText);
        return m.find() ? new int[] {Integer.parseInt(m.group(1)), -1} : null;
    }

    public static Score score(String jobTitle, String jobDescription, String jobLocation, NaukriProfile p) {
        String jobText = (jobTitle == null ? "" : jobTitle) + "\n" + (jobDescription == null ? "" : jobDescription);
        String profile = " " + String.join(" ", nz(p.headline()), nz(p.keySkills()), nz(p.designation())).toLowerCase(Locale.ROOT) + " ";
        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        double total = 0;

        List<String> wanted = skillsIn(jobText);
        if (!wanted.isEmpty()) {
            Set<String> have = new LinkedHashSet<>();
            for (String s : wanted) {
                if (contains(profile, s)) {
                    have.add(s);
                }
            }
            total += 50.0 * Math.min(1.0, have.size() / (double) Math.min(wanted.size(), 8));
            if (!have.isEmpty()) {
                strengths.add("Mentions " + String.join(", ", have));
            }
            List<String> missing = wanted.stream().filter(s -> !have.contains(s)).limit(4).toList();
            if (!missing.isEmpty()) {
                gaps.add("Not in profile: " + String.join(", ", missing));
            }
        } else {
            total += 25; // nothing to match against: neutral
        }

        int[] years = yearsIn(jobText);
        Double exp = p.experienceYears();
        if (years == null || exp == null) {
            total += 12.5;
        } else {
            int min = years[0];
            int max = years[1];
            if (exp >= min && (max < 0 || exp <= max + 1)) {
                total += 25;
                strengths.add(fmt(exp) + " years' experience (asked " + min + (max < 0 ? "+" : "–" + max) + ")");
            } else if (exp < min) {
                total += Math.max(0, 25 - (min - exp) * 10);
                gaps.add(fmt(exp) + " years' experience; asked " + min + (max < 0 ? "+" : "–" + max));
            } else {
                total += 15;
                gaps.add(fmt(exp) + " years' experience, above the " + min + "–" + max + " asked");
            }
        }

        List<String> titleWords = jobTitle == null ? List.of() : List.of(jobTitle.toLowerCase(Locale.ROOT).split("[^a-z0-9.+#]+")).stream()
                .filter(w -> w.length() > 1 && !TITLE_STOP.contains(w)).toList();
        String designation = nz(p.designation()).toLowerCase(Locale.ROOT) + " " + nz(p.headline()).toLowerCase(Locale.ROOT);
        if (!titleWords.isEmpty()) {
            long hit = titleWords.stream().filter(w -> contains(" " + designation + " ", w)).count();
            total += 15.0 * hit / titleWords.size();
        } else {
            total += 7.5;
        }

        if (jobLocation != null && !jobLocation.isBlank()) {
            String locs = (nz(p.currentLocation()) + " " + nz(p.preferredLocations())).toLowerCase(Locale.ROOT);
            boolean near = List.of(jobLocation.toLowerCase(Locale.ROOT).split("[,/]")).stream()
                    .map(String::strip).filter(s -> !s.isEmpty())
                    .anyMatch(s -> locs.contains(s) || (s.contains("bengaluru") || s.contains("bangalore")) && (locs.contains("bengaluru") || locs.contains("bangalore")));
            if (near) {
                total += 5;
                strengths.add("Based in or open to " + jobLocation.strip());
            } else if (!locs.isBlank()) {
                gaps.add("Location: " + nz(p.currentLocation()));
            }
        } else {
            total += 2.5;
        }

        String notice = nz(p.noticePeriod()).toLowerCase(Locale.ROOT);
        if (notice.contains("15 days") || notice.contains("immediate") || notice.contains("serving")) {
            total += 5;
            strengths.add("Notice: " + p.noticePeriod());
        } else if (notice.contains("1 month") || notice.contains("30 days")) {
            total += 3;
        } else if (notice.contains("2 month") || notice.contains("3 month") || notice.contains("more than")) {
            gaps.add("Notice: " + p.noticePeriod());
        } else {
            total += 2.5;
        }
        return new Score((int) Math.round(Math.min(100, total)), strengths, gaps);
    }

    private static boolean contains(String haystack, String skill) {
        // "java" may run into the next word ("JavaFullStack", "Java8") but never into "script".
        String s = skill.equals("java") ? "java(?!\\s*script)" : Pattern.quote(skill) + "(?![a-z0-9])";
        return Pattern.compile("(?<![a-z0-9])" + s).matcher(haystack).find();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(double years) {
        return years == Math.floor(years) ? String.valueOf((int) years) : String.valueOf(years);
    }
}
