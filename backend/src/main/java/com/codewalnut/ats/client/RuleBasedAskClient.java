package com.codewalnut.ats.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ask ATS without AI, for the dev and demo profiles (ADR-0021): picks one lookup from keywords and
 * lists what it returns. Enough to try the chat and its links; real questions need Claude.
 */
public class RuleBasedAskClient implements AskClient {

    private static final Map<String, String> STAGES = Map.ofEntries(
            Map.entry("applied", "SOURCED"), Map.entry("sourced", "SOURCED"), Map.entry("screening", "SCREENING"),
            Map.entry("interviewed", "INTERVIEWED"), Map.entry("shortlisted", "SHORTLISTED"),
            Map.entry("submitted", "SUBMITTED_TO_CLIENT"), Map.entry("selected", "SELECTED"),
            Map.entry("offer", "OFFER_SENT"), Map.entry("joined", "JOINED"), Map.entry("rejected", "REJECTED"),
            Map.entry("on hold", "ON_HOLD"));
    private static final Pattern STOP = Pattern.compile(
            "(?i)\\b(who|whom|what|which|is|are|the|a|an|of|in|for|about|show|me|list|find|tell|candidate|candidates|"
                    + "status|details|stage|please|any|all|do|does|we|have|has|with|and|how|many)\\b");

    private final ObjectMapper objectMapper;

    public RuleBasedAskClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String model() {
        return "rules";
    }

    @Override
    public Answer answer(List<Turn> history, String guide, List<ToolSpec> tools, ToolBox toolBox) {
        // Drop the "[Asked by …]" line the service adds for the AI.
        String q = history.get(history.size() - 1).text().replaceFirst("^\\[Asked by [^\\]]*\\]\\n", "").toLowerCase(Locale.ROOT);
        try {
            java.util.regex.Matcher move = MOVE.matcher(q);
            if (move.matches()) {
                return new Answer(proposeMoves(move.group(1), move.group(2), toolBox), List.of("search_candidates", "propose_actions"));
            }
            if (q.startsWith("how do i") || q.startsWith("how to") || q.startsWith("how can i")) {
                return new Answer(howTo(q, guide), List.of());
            }
            if (q.contains("feedback")) {
                return new Answer(interviews(toolBox.call("list_interviews", Map.of("when", "feedback_due")),
                        "Interviews waiting for your feedback"), List.of("list_interviews"));
            }
            if (q.contains("interview")) {
                return new Answer(interviews(toolBox.call("list_interviews", Map.of("when", "upcoming")),
                        "Upcoming interviews"), List.of("list_interviews"));
            }
            if (q.contains("test") || q.contains("result")) {
                return new Answer(list(toolBox.call("test_results", Map.of()), "newResults", "New test results",
                        r -> r.get("candidate") + " — " + r.get("test") + ": " + r.get("percent") + "%"), List.of("test_results"));
            }
            if (q.contains("opening") || q.contains("job") || q.contains("position")) {
                return new Answer(list(toolBox.call("list_openings", Map.of("status", "OPEN")), "openings", "Open openings",
                        r -> "[" + r.get("title") + "](/jobs/" + r.get("jobId") + ") (" + r.get("client") + "): "
                                + r.get("candidates") + " candidates"), List.of("list_openings"));
            }
            for (Map.Entry<String, String> stage : STAGES.entrySet()) {
                if (q.contains(stage.getKey())) {
                    return new Answer(candidates(toolBox.call("search_candidates", Map.of("stage", stage.getValue()))),
                            List.of("search_candidates"));
                }
            }
            String name = STOP.matcher(q.replaceAll("[^\\p{L}\\p{N}@. ]", " ")).replaceAll(" ").replaceAll("\\s+", " ").strip();
            return new Answer(candidates(toolBox.call("search_candidates", name.isEmpty() ? Map.of() : Map.of("query", name))),
                    List.of("search_candidates"));
        } catch (IllegalArgumentException e) {
            return new Answer(e.getMessage(), List.of());
        }
    }

    private static final Pattern MOVE = Pattern.compile("move (.+?) to (.+?)[.!]?");

    /** "move asha and ravi to shortlisted": find each person, then propose the moves as cards. */
    private String proposeMoves(String who, String where, ToolBox toolBox) {
        String stage = STAGES.entrySet().stream().filter(e -> where.contains(e.getKey())).map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (stage == null) {
            return "Which stage? For example: move Asha to shortlisted.";
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String name : who.split("\\s*(,|\\band\\b|&)\\s*")) {
            if (name.isBlank()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> found = (List<Map<String, Object>>) read(toolBox.call("search_candidates",
                    Map.of("query", name.strip()))).getOrDefault("candidates", List.of());
            if (found.isEmpty()) {
                notFound.add(name.strip());
            } else {
                actions.add(Map.of("type", "MOVE_STAGE", "applicationId", String.valueOf(found.get(0).get("applicationId")), "stage", stage));
            }
        }
        List<String> out = new ArrayList<>();
        if (!actions.isEmpty()) {
            Map<String, Object> result = read(toolBox.call("propose_actions", Map.of("actions", actions)));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> refused = (List<Map<String, Object>>) result.getOrDefault("refused", List.of());
            out.add("Here's what I can do. Check each card and click **Do it**.");
            refused.forEach(r -> out.add("- Not possible: " + r.get("reason")));
        }
        notFound.forEach(n -> out.add("- I couldn't find " + n + "."));
        out.add("");
        out.add("_Answered without AI (dev mode)._");
        return String.join("\n", out);
    }

    private String candidates(String json) {
        return list(json, "candidates", "Candidates",
                r -> "[" + r.get("name") + "](/jobs/" + r.get("jobId") + "?candidate=" + r.get("applicationId") + ") — "
                        + r.get("opening") + ", " + r.get("stage"));
    }

    private String interviews(String json, String title) {
        return list(json, "interviews", title, r -> r.get("candidate") + " — " + r.get("opening") + ", " + r.get("startAt")
                + " ([Feedback](/interviews/" + r.get("interviewId") + "/feedback))");
    }

    private String list(String json, String key, String title, java.util.function.Function<Map<String, Object>, String> line) {
        Map<String, Object> data = read(json);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.getOrDefault(key, List.of());
        if (rows.isEmpty()) {
            return "**" + title + ":** none found.";
        }
        List<String> out = new ArrayList<>();
        out.add("**" + title + "** (" + rows.size() + "):");
        out.add("");
        rows.stream().limit(25).forEach(r -> out.add("- " + line.apply(r)));
        out.add("");
        out.add("_Answered without AI (dev mode): set up the AI for full answers._");
        return String.join("\n", out);
    }

    /** The guide section whose heading or lines share the most words with the question. */
    static String howTo(String question, String guide) {
        String best = null;
        int bestScore = 0;
        for (String line : guide.replaceAll("\n {2,}", " ").split("\n")) {
            if (!line.startsWith("- ")) {
                continue;
            }
            int score = 0;
            String l = line.toLowerCase(Locale.ROOT);
            for (String word : question.split("\\W+")) {
                if (word.length() > 3 && l.contains(word)) {
                    score++;
                }
            }
            if (score > 0 && line.contains("→")) {
                score++; // a line with click steps answers "how do I…" best
            }
            if (score > bestScore) {
                bestScore = score;
                best = line.substring(2);
            }
        }
        return best == null ? "I couldn't find that in the guide. Try What's new in the menu." : best;
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Couldn't read the lookup result.");
        }
    }
}
