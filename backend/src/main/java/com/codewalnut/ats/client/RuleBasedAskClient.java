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
        String q = history.get(history.size() - 1).text().toLowerCase(Locale.ROOT);
        try {
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
