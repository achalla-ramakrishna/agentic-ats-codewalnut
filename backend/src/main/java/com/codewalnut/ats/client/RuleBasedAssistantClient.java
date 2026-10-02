package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple, offline stand-in for the AI assistant, used in the dev and demo profiles and in
 * tests: "sagar, sucheth and amogh are shortlisted" style instructions only. Nothing leaves the app.
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

    @Override
    public AssistantPlan plan(Request request) {
        String text = request.instruction().strip();
        String lower = text.toLowerCase(Locale.ROOT);
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
}
