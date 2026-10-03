package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple, offline stand-in for the AI assistant, used in the dev and demo profiles and in
 * tests: "sagar, sucheth and amogh are shortlisted" style instructions, and keyword questions over
 * the résumé profiles. Nothing leaves the app.
 */
public class RuleBasedAssistantClient implements AssistantClient {

    private static final Map<String, String> STAGE_WORDS = new LinkedHashMap<>();

    static {
        STAGE_WORDS.put("shortlist", "SHORTLISTED");
        STAGE_WORDS.put("reject", "REJECTED");
        STAGE_WORDS.put("not selected", "REJECTED");
        STAGE_WORDS.put("withdr", "WITHDRAWN");
        STAGE_WORDS.put("on hold", "ON_HOLD");
        STAGE_WORDS.put("offer accepted", "OFFER_ACCEPTED");
        STAGE_WORDS.put("offer", "OFFER_SENT");
        STAGE_WORDS.put("joined", "JOINED");
        STAGE_WORDS.put("selected", "SELECTED");
        STAGE_WORDS.put("hired", "SELECTED");
        STAGE_WORDS.put("client interview", "CLIENT_INTERVIEW");
        STAGE_WORDS.put("submitted", "SUBMITTED_TO_CLIENT");
        STAGE_WORDS.put("interviewed", "INTERVIEWED");
        STAGE_WORDS.put("screen", "SCREENING");
        STAGE_WORDS.put("applied", "SOURCED");
    }

    private static final Pattern REASON = Pattern.compile("(?i)\\b(?:because|reason:?|as)\\b(.+)$");
    private static final Pattern FILLER = Pattern.compile(
            "(?i)\\b(are|is|was|were|have been|has been|got|been|to|move|moved|mark|marked|as|the|candidates?|now|please)\\b");

    @Override
    public boolean available() {
        return true;
    }

    private static final Pattern QUESTION = Pattern.compile(
            "(?i)^(can you|could you|tell me|who|whom|which|what|how|list|show|find|compare|rank|any|does|do|is there|are there|give)\\b.*|.*\\b(list of|how many)\\b.*|.*\\?\\s*$");
    private static final Pattern NOT_INTERVIEWED = Pattern.compile("(not|n't|never|yet to)( been| be)? interview");
    private static final Set<String> EARLY_STAGES = Set.of("Applied / Sourced", "Screening");
    private static final Pattern FIT = Pattern.compile("fit (\\d+)%");
    private static final Set<String> QUESTION_WORDS = Set.of("who", "whom", "which", "what", "how", "list", "show",
            "find", "compare", "rank", "any", "does", "the", "and", "with", "has", "have", "are", "there", "give", "candidates",
            "candidate", "people", "knows", "know", "from", "for", "should", "call", "contact", "first", "best", "top",
            "good", "experience", "skills", "skill", "can", "most", "suitable", "fit", "fits", "this", "role", "opening");
    private static final Pattern RANKING = Pattern.compile("(?i)\\b(best|first|top|rank|call|contact|suitable|closest)\\b");

    @Override
    public AssistantPlan plan(Request request) {
        String text = request.instruction().strip();
        String lower = text.toLowerCase(Locale.ROOT);
        if (QUESTION.matcher(text).matches()) {
            return answer(request, lower);
        }
        String stage = null;
        int stageAt = -1;
        for (Map.Entry<String, String> e : STAGE_WORDS.entrySet()) {
            int at = lower.indexOf(e.getKey());
            if (at >= 0 && (stageAt < 0 || at < stageAt)) {
                stage = e.getValue();
                stageAt = at;
            }
        }
        if (stage == null) {
            return new AssistantPlan("I couldn't tell which stage you meant.", List.of(), List.of());
        }
        String reason = "";
        Matcher m = REASON.matcher(text.substring(stageAt));
        if (m.find()) {
            reason = m.group(1).strip();
        }
        String namesPart = text.substring(0, stageAt);
        List<AssistantPlan.Action> actions = new ArrayList<>();
        List<AssistantPlan.Unresolved> unresolved = new ArrayList<>();
        for (String raw : namesPart.split("(?i),|\\band\\b|&")) {
            String mention = FILLER.matcher(raw).replaceAll(" ").replaceAll("\\s+", " ").strip();
            if (mention.isEmpty()) {
                continue;
            }
            String needle = mention.toLowerCase(Locale.ROOT);
            List<Candidate> hits = request.candidates().stream()
                    .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
            if (hits.size() == 1) {
                actions.add(new AssistantPlan.Action("MOVE_STAGE", hits.get(0).applicationId(), stage, reason));
            } else {
                unresolved.add(new AssistantPlan.Unresolved(mention,
                        hits.stream().map(Candidate::applicationId).toList(), stage, reason));
            }
        }
        return new AssistantPlan("Move " + (actions.size() + unresolved.size()) + " candidate(s).", actions, unresolved);
    }

    /** Keyword search over the résumé profiles; "best / first / top" questions rank by match score. */
    private AssistantPlan answer(Request request, String lower) {
        if (NOT_INTERVIEWED.matcher(lower).find()) {
            List<AssistantPlan.Match> waiting = request.candidates().stream()
                    .filter(c -> EARLY_STAGES.contains(c.stageLabel()))
                    .map(c -> new AssistantPlan.Match(c.applicationId(), "At " + c.stageLabel()))
                    .toList();
            return new AssistantPlan("Answered from the candidates' stages.", List.of(), List.of(),
                    waiting.size() + " candidate(s) haven't been interviewed yet.", waiting);
        }
        List<String> words = Arrays.stream(lower.split("[^a-z0-9+#.]+"))
                .map(w -> w.replaceAll("^\\.+|\\.+$", ""))
                .filter(w -> w.length() >= 2 && !QUESTION_WORDS.contains(w))
                .distinct().toList();
        boolean ranking = RANKING.matcher(lower).find();
        List<Candidate> profiled = request.candidates().stream().filter(c -> c.profile() != null).toList();
        if (profiled.isEmpty()) {
            return new AssistantPlan("No résumés have been read yet.", List.of(), List.of(),
                    "None of the candidates' résumés have been read yet. Upload or analyze résumés first.", List.of());
        }
        List<AssistantPlan.Match> matches = new ArrayList<>();
        profiled.stream()
                .map(c -> Map.entry(c, (int) words.stream()
                        .filter(w -> (evidence(c) + " " + c.name()).toLowerCase(Locale.ROOT).contains(w)).count()))
                .filter(e -> words.isEmpty() ? ranking : e.getValue() > 0)
                .sorted(Comparator.comparing((Map.Entry<Candidate, Integer> e) -> -e.getValue())
                        .thenComparing(e -> -fit(e.getKey())))
                .limit(15)
                .forEach(e -> matches.add(new AssistantPlan.Match(e.getKey().applicationId(),
                        words.isEmpty() ? "Match " + fit(e.getKey()) + "%"
                                : "Profile mentions " + words.stream()
                                        .filter(w -> evidence(e.getKey()).toLowerCase(Locale.ROOT).contains(w))
                                        .collect(Collectors.joining(", ")))));
        String answer = matches.isEmpty()
                ? "No candidate's résumé reading mentions " + String.join(", ", words) + "."
                : matches.size() + " candidate(s) match" + (words.isEmpty() ? ", best match first." : ": " + String.join(", ", words) + ".");
        return new AssistantPlan("Answered from the résumé readings (offline keyword search).", List.of(), List.of(),
                answer, matches);
    }

    /** The profile without the "not evident" requirements, so a missing skill doesn't count as a hit. */
    private static String evidence(Candidate c) {
        return c.profile().replaceAll("not evident: [^·]*", "");
    }

    private static int fit(Candidate c) {
        Matcher m = FIT.matcher(c.profile() == null ? "" : c.profile());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
