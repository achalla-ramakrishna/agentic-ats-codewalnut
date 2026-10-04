package com.codewalnut.ats.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * A self-hosted Judge0 CE sandbox (ADR-0016). Submissions go in batches; results are polled until
 * every one has finished. Only source code and inputs are sent, never the expected outputs.
 */
@Slf4j
public class Judge0CodeRunner implements CodeRunner {

    /** Judge0 accepts at most 20 submissions per batch by default. */
    static final int BATCH = 20;
    private static final Duration POLL_EVERY = Duration.ofMillis(400);
    private static final Duration GIVE_UP_AFTER = Duration.ofSeconds(90);
    private static final int MAX_TEXT = 20_000;

    private final RestClient http;
    private final Map<String, Integer> languageIds;

    public Judge0CodeRunner(RestClient http, Map<String, Integer> languageIds) {
        this.http = http;
        this.languageIds = languageIds;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public List<Result> run(String language, String source, List<String> inputs, Limits limits) {
        Integer languageId = languageIds.get(language);
        if (languageId == null) {
            throw new IllegalArgumentException("language: not supported: " + language);
        }
        List<Result> results = new ArrayList<>();
        for (int from = 0; from < inputs.size(); from += BATCH) {
            List<String> chunk = inputs.subList(from, Math.min(inputs.size(), from + BATCH));
            results.addAll(runBatch(languageId, source, chunk, limits));
        }
        return results;
    }

    private List<Result> runBatch(int languageId, String source, List<String> inputs, Limits limits) {
        List<Map<String, Object>> submissions = new ArrayList<>();
        for (String input : inputs) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("language_id", languageId);
            s.put("source_code", b64(source));
            s.put("stdin", b64(input));
            s.put("cpu_time_limit", limits.cpuSeconds());
            s.put("wall_time_limit", Math.max(5, limits.cpuSeconds() * 3));
            s.put("memory_limit", limits.memoryMb() * 1024);
            submissions.add(s);
        }
        List<String> tokens = new ArrayList<>();
        try {
            JsonNode created = http.post().uri("/submissions/batch?base64_encoded=true")
                    .body(Map.of("submissions", submissions)).retrieve().body(JsonNode.class);
            for (JsonNode n : created) {
                if (!n.hasNonNull("token")) {
                    throw new UnavailableException("Judge0 refused a submission: " + n, null);
                }
                tokens.add(n.get("token").asText());
            }
            Instant giveUp = Instant.now().plus(GIVE_UP_AFTER);
            while (true) {
                JsonNode batch = http.get().uri("/submissions/batch?tokens={t}&base64_encoded=true"
                        + "&fields=token,stdout,stderr,compile_output,message,status,time,memory", String.join(",", tokens))
                        .retrieve().body(JsonNode.class);
                List<JsonNode> subs = new ArrayList<>();
                batch.path("submissions").forEach(subs::add);
                boolean done = subs.size() == tokens.size() && subs.stream().allMatch(n -> n.path("status").path("id").asInt() > 2);
                if (done) {
                    return subs.stream().map(Judge0CodeRunner::result).toList();
                }
                if (Instant.now().isAfter(giveUp)) {
                    throw new UnavailableException("Judge0 didn't finish in time", null);
                }
                Thread.sleep(POLL_EVERY.toMillis());
            }
        } catch (RestClientException e) {
            throw new UnavailableException("Judge0 call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("Interrupted while waiting for Judge0", e);
        }
    }

    /** Judge0 status ids: 3 accepted, 5 time limit, 6 compile error, 7–12 runtime errors, 13–14 internal. */
    static Result result(JsonNode n) {
        int id = n.path("status").path("id").asInt();
        String stderr = text(n, "stderr");
        Status status = switch (id) {
            case 3, 4 -> Status.OK;
            case 5 -> Status.TIME_LIMIT;
            case 6 -> Status.COMPILE_ERROR;
            case 7, 8, 9, 10, 11, 12 -> stderr != null && stderr.contains("OutOfMemory") ? Status.MEMORY_LIMIT : Status.RUNTIME_ERROR;
            default -> Status.INTERNAL_ERROR;
        };
        if (status == Status.INTERNAL_ERROR) {
            log.warn("Judge0 internal error {}: {}", id, text(n, "message"));
        }
        Double time = n.hasNonNull("time") ? n.get("time").asDouble() : null;
        Integer memory = n.hasNonNull("memory") ? n.get("memory").asInt() : null;
        return new Result(status, text(n, "stdout"), stderr, text(n, "compile_output"), time, memory);
    }

    private static String text(JsonNode n, String field) {
        if (!n.hasNonNull(field)) {
            return null;
        }
        String s = new String(Base64.getMimeDecoder().decode(n.get(field).asText()), StandardCharsets.UTF_8);
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + "\n…" : s;
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }
}
