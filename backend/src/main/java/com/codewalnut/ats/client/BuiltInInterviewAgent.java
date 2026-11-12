package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The interview agent without a model (ADR-0030): a standard question set for the opening, and a
 * basic review by rules (how much was said, the opening's keywords, marked answers). Used in
 * dev/demo and when ANTHROPIC_API_KEY isn't set; the UI labels its reviews "basic, not AI".
 */
public class BuiltInInterviewAgent implements InterviewAgent {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}+#.]+");
    private static final Set<String> COMMON = Set.of("with", "that", "this", "from", "will", "have", "your", "team", "work",
            "role", "years", "experience", "good", "strong", "skills", "knowledge", "about", "they", "their", "what", "should",
            "able", "must", "using", "development", "developer", "senior", "engineer", "software", "application", "applications");

    @Override
    public boolean usesAi() {
        return false;
    }

    @Override
    public String model() {
        return "rules";
    }

    @Override
    public Draft draft(Job job) {
        String title = job.title() == null || job.title().isBlank() ? "this" : job.title().strip();
        boolean java = (job.title() + " " + job.description()).toLowerCase(Locale.ROOT).contains("java");
        List<DraftQuestion> q = new ArrayList<>();
        q.add(video("INTRO", "Introduce yourself in about a minute: your current role, your years of experience and the kind of work you enjoy.", 60));
        q.add(video("EXPERIENCE", "Walk us through your current role. What are you responsible for day to day, and what does your team build?", 120));
        q.add(video("EXPERIENCE", "Tell us about the work you've done that is closest to this " + title + " role. What was your own part in it?", 120));
        q.add(video("SKILLS", "Which skills that this role needs are you strongest in? Give a concrete example of using each one.", 120));
        if (java) {
            q.add(blank("In Java, the keyword that makes writes to a field visible to other threads without locking is ____.", "volatile"));
            q.add(blank("In Spring, the annotation that runs a method inside a database transaction is @____.", "Transactional"));
            q.add(blank("In the JVM, objects created with new are allocated on the ____.", "heap"));
        }
        q.add(video("PROJECTS", "Describe a project you're proud of: the problem, the tech stack, and what you personally built.", 180));
        q.add(video("PROJECTS", "What was the hardest technical problem on that project, and how did you solve it?", 120));
        q.add(video("ARCHITECTURE", "How would you design a service that must cope with a sudden ten-fold jump in traffic? Talk through the main parts and the trade-offs.", 180));
        q.add(video("ARCHITECTURE", "Tell us about a design decision you made that you would change today, and why.", 120));
        q.add(video("TECHNICAL", "A production API has become slow over the last week. How do you find and fix the cause? Walk us through your steps.", 180));
        q.add(video("TECHNICAL", java
                ? "How would you make shared code thread-safe in Java? Compare the options you would consider and when you'd pick each."
                : "Explain a concept from your main tech stack that people often get wrong, with an example.", 120));
        q.add(video("SOFT_SKILLS", "Tell us about a time you disagreed with a teammate or a client. What happened, and how did it end?", 120));
        q.add(video("SOFT_SKILLS", "A deadline is at risk. How do you keep your client or manager informed, and what do you do next?", 120));
        q.add(written("READINESS", "What is your notice period, and the earliest date you could join?"));
        q.add(written("READINESS", "What are your current and expected salary (CTC)?"));
        q.add(written("READINESS", "Where are you based, and are you open to relocating or working from the office?"));
        q.add(video("READINESS", "Why are you interested in this role at CodeWalnut?", 60));
        return new Draft(q);
    }

    @Override
    public Review review(Job job, List<Answer> answers) {
        Set<String> keywords = keywords(job);
        Map<Area, List<Answer>> byArea = new LinkedHashMap<>();
        for (Area a : Area.values()) {
            byArea.put(a, new ArrayList<>());
        }
        for (Answer a : answers) {
            byArea.get(area(a.phase())).add(a);
            if (!"COMMUNICATION".equals(area(a.phase()).name()) && "VIDEO".equals(a.kind())) {
                byArea.get(Area.COMMUNICATION).add(a); // every spoken answer shows how they communicate
            }
        }
        List<AreaRating> ratings = new ArrayList<>();
        for (Map.Entry<Area, List<Answer>> e : byArea.entrySet()) {
            ratings.add(rate(e.getKey(), e.getValue(), keywords));
        }
        Map<String, List<Answer>> byPhase = new LinkedHashMap<>();
        answers.forEach(a -> byPhase.computeIfAbsent(a.phase(), k -> new ArrayList<>()).add(a));
        List<PhaseNote> phases = new ArrayList<>();
        byPhase.forEach((phase, list) -> {
            int words = list.stream().mapToInt(a -> words(a.answer())).sum();
            phases.add(new PhaseNote(phase, lengthRating(words / Math.max(1, list.size())), words + " words across " + list.size() + " answer(s)."));
        });
        String notice = find(answers, "notice");
        String salary = find(answers, "salary");
        return new Review(ratings, phases,
                "Basic review by rules, not AI: it counts how much the candidate said and which of the opening's keywords they used. "
                        + "Watch the videos before deciding.",
                List.of(), List.of("Listen to the answers: this review can't judge whether they are correct."), notice, salary);
    }

    private AreaRating rate(Area area, List<Answer> list, Set<String> keywords) {
        if (list.isEmpty()) {
            return new AreaRating(area.name(), 0, "No answers in this area.", "");
        }
        List<Answer> marked = list.stream().filter(a -> a.correct() != null).toList();
        List<Answer> open = list.stream().filter(a -> a.correct() == null).toList();
        int words = open.stream().mapToInt(a -> words(a.answer())).sum();
        // The opening's keywords, plus what the team expects a strong answer to cover, where they wrote it.
        long hits = open.stream().filter(a -> words(a.answer()) > 0)
                .flatMap(a -> {
                    Set<String> expected = new java.util.HashSet<>(keywords);
                    tokens(a.goodAnswer()).stream().filter(t -> t.length() > 3 && !COMMON.contains(t)).forEach(expected::add);
                    return tokens(a.answer()).stream().filter(expected::contains);
                })
                .distinct().count();
        int rating = open.isEmpty() ? 0 : Math.min(5, lengthRating(words / open.size()) + (hits >= 3 ? 1 : 0));
        if (!marked.isEmpty()) {
            int right = (int) marked.stream().filter(Answer::correct).count();
            int markedRating = Math.max(1, Math.round(5f * right / marked.size()));
            rating = rating == 0 ? markedRating : Math.round((rating + markedRating) / 2f);
        }
        boolean heard = open.stream().anyMatch(a -> words(a.answer()) > 0);
        String reason = (open.isEmpty() ? "" : heard ? words + " words said, " + hits + " of the opening's keywords used"
                : "no transcript to read — watch the videos")
                + (marked.isEmpty() ? "" : (open.isEmpty() ? "" : "; ") + marked.stream().filter(Answer::correct).count() + "/" + marked.size()
                        + " marked answers right") + ".";
        String evidence = open.stream().map(Answer::answer).filter(s -> words(s) > 0).findFirst()
                .map(s -> s.length() > 140 ? s.substring(0, 140) + "…" : s).orElse("");
        return new AreaRating(area.name(), rating, reason, evidence);
    }

    static Area area(String phase) {
        return switch (phase == null ? "" : phase) {
            case "SKILLS", "TECHNICAL" -> Area.TECHNICAL;
            case "ARCHITECTURE" -> Area.ARCHITECTURE;
            case "EXPERIENCE", "PROJECTS" -> Area.EXPERIENCE;
            case "READINESS" -> Area.READINESS;
            default -> Area.COMMUNICATION;
        };
    }

    private static int lengthRating(int words) {
        return words == 0 ? 0 : words < 25 ? 1 : words < 60 ? 2 : words < 120 ? 3 : words < 200 ? 4 : 5;
    }

    private static String find(List<Answer> answers, String about) {
        return answers.stream().filter(a -> a.question() != null && a.question().toLowerCase(Locale.ROOT).contains(about))
                .map(Answer::answer).filter(s -> s != null && !s.isBlank()).findFirst()
                .map(s -> s.length() > 120 ? s.substring(0, 120) + "…" : s).orElse("");
    }

    private static Set<String> keywords(Job job) {
        Set<String> out = new java.util.HashSet<>();
        for (String t : tokens(job.title() + " " + job.description())) {
            if (t.length() > 3 && !COMMON.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        var m = WORD.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group().replaceAll("\\.+$", ""));
        }
        return out;
    }

    /** Words said; the "(answered on video; no transcript …)" placeholder counts as none. */
    private static int words(String s) {
        return s == null || s.startsWith("(") ? 0 : tokens(s).size();
    }

    private static DraftQuestion video(String phase, String prompt, int seconds) {
        return new DraftQuestion(phase, "VIDEO", prompt, List.of(), seconds);
    }

    private static DraftQuestion written(String phase, String prompt) {
        return new DraftQuestion(phase, "WRITTEN", prompt, List.of(), 0);
    }

    private static DraftQuestion blank(String prompt, String answer) {
        return new DraftQuestion("SKILLS", "FILL_BLANK", prompt, List.of(answer), 0);
    }
}
