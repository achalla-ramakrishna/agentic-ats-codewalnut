package com.codewalnut.ats.client;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An offline stand-in for AI résumé reading, used in the dev and demo profiles and in tests:
 * plain keyword matching of the opening's bullet points against the résumé text. Nothing
 * leaves the app.
 */
public class KeywordResumeAnalyzer implements ResumeAnalyzer {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d -]{8,16}\\d");
    private static final Pattern YEAR = Pattern.compile("\\b(20[0-4]\\d)\\b");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z.' ]{1,60}$");
    private static final Set<String> STOP = Set.of("and", "the", "with", "for", "any", "you", "your", "are", "our",
            "have", "has", "who", "will", "can", "from", "that", "this", "should", "must", "able", "good", "strong",
            "knowledge", "experience", "understanding", "skills", "working", "basic", "plus", "nice", "using", "etc");
    private static final List<String> SKILLS = List.of("Java", "Spring Boot", "Spring", "React", "TypeScript",
            "JavaScript", "Python", "SQL", "MySQL", "PostgreSQL", "AWS", "Docker", "Kubernetes", "Node.js", "HTML",
            "CSS", "Git", "Angular", "Go", "C++", "REST", "Figma");

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String model() {
        return "offline-keywords";
    }

    @Override
    public ResumeInsight analyze(Job job, ResumeFile file) {
        String text;
        if (ResumeText.isPdf(file)) {
            text = ResumeText.pdfBestEffort(file.data());
        } else if (ResumeText.isDocx(file)) {
            text = ResumeText.docx(file.data());
        } else {
            throw new CalendarException("Old Word (.doc) files can't be read. Please save it as PDF or .docx and upload again.");
        }
        if (text.isBlank()) {
            throw new CalendarException("No text could be read from this file.");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String name = Arrays.stream(text.split("\\n"))
                .map(String::strip)
                .filter(l -> NAME.matcher(l).matches() && l.split("\\s+").length >= 2 && l.split("\\s+").length <= 4)
                .findFirst().orElse("");
        Matcher email = EMAIL.matcher(text);
        Matcher phone = PHONE.matcher(text);
        int graduation = 0;
        Matcher year = YEAR.matcher(text);
        while (year.find()) {
            int y = Integer.parseInt(year.group(1));
            if (y <= Year.now().getValue() + 4 && y > graduation) {
                graduation = y;
            }
        }
        List<String> skills = SKILLS.stream()
                .filter(s -> Pattern.compile("(?<![A-Za-z])" + Pattern.quote(s.toLowerCase(Locale.ROOT)) + "(?![A-Za-z])")
                        .matcher(lower).find())
                .toList();
        List<ResumeInsight.Requirement> requirements = new ArrayList<>();
        for (String line : (job.description() == null ? "" : job.description()).split("\\n")) {
            String requirement = line.strip();
            if (!requirement.matches("^[-*•].*")) {
                continue;
            }
            requirement = requirement.replaceFirst("^[-*•]\\s*", "");
            List<String> words = Arrays.stream(requirement.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+"))
                    .map(w -> w.replaceAll("^\\.+|\\.+$", ""))
                    .filter(w -> w.length() >= 2 && !STOP.contains(w))
                    .distinct().toList();
            if (words.isEmpty()) {
                continue;
            }
            List<String> found = words.stream().filter(lower::contains).toList();
            double share = (double) found.size() / words.size();
            String assessment = share >= 0.6 ? "MET" : share >= 0.25 ? "PARTIAL" : "NOT_EVIDENT";
            requirements.add(new ResumeInsight.Requirement(requirement, assessment,
                    found.isEmpty() ? "not evident in résumé" : "mentions " + String.join(", ", found)));
        }
        long met = requirements.stream().filter(r -> r.assessment().equals("MET")).count();
        String headline = requirements.isEmpty()
                ? "Offline reading: " + skills.size() + " known skills found in the résumé text."
                : "Offline reading: " + met + " of " + requirements.size() + " requirements found in the résumé text.";
        return new ResumeInsight(name, email.find() ? email.group() : "",
                phone.find() ? phone.group().replaceAll("[ -]", "") : "", "", "", 0, graduation, "", skills,
                List.of(), List.of(), headline, requirements,
                skills.stream().limit(4).map(s -> "Mentions " + s).toList(),
                requirements.stream().filter(r -> r.assessment().equals("NOT_EVIDENT")).limit(4)
                        .map(r -> r.requirement() + ": not evident in résumé").toList(),
                List.of());
    }
}
