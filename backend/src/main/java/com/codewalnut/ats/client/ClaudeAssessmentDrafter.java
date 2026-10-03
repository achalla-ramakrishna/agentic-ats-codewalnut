package com.codewalnut.ats.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import lombok.extern.slf4j.Slf4j;

/** Drafts test questions with Claude (structured output). A person reviews every one. */
@Slf4j
public class ClaudeAssessmentDrafter implements AssessmentDrafter {

    static final String SYSTEM = """
            You write screening test questions for CodeWalnut, an Indian software services company hiring \
            freshers, interns and engineers. Questions are taken online against a timer and scored \
            automatically, so every question must have one unambiguous correct answer.

            Write a mix of multiple-choice questions and short code-reading questions ("What does this \
            print?"). For code, keep it short (at most 15 lines), self-contained and valid for current \
            Java 21 / Python 3.12 / modern JavaScript; trace it carefully and double-check the answer \
            before giving it. Options must be plausible and distinct. SHORT_ANSWER answers must be short \
            and exact (a number, a word, or the exact printed output). Avoid trick questions about obscure \
            trivia, avoid questions that need a calculator beyond simple arithmetic, and avoid anything \
            about personal life, culture, religion or region. Use Indian-English contexts (₹, km) where \
            natural. Don't repeat the questions listed in <avoid>.

            For data-interpretation questions, give the data in chart (BAR, LINE, PIE or TABLE) instead of \
            describing it in words; the app draws it. Use whole numbers, make pie values add up to 100, and \
            compute the answer from exactly those numbers.""";

    private final AnthropicClient client;
    private final String model;

    public ClaudeAssessmentDrafter(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public AssessmentDraft draft(Request request) {
        String avoid = request.avoid().isEmpty() ? "(none)" : String.join("\n", request.avoid());
        String user = "<test>\nTitle: " + clean(request.title()) + "\nCategory: " + request.category()
                + "\nLevel: " + clean(request.level()) + "\nTopic or focus: " + clean(request.topic())
                + "\n</test>\n<avoid>\n" + avoid.replace("</avoid>", "") + "\n</avoid>\n"
                + "Write " + request.count() + " questions.";
        StructuredMessageCreateParams<AssessmentDraft> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM)
                .outputConfig(AssessmentDraft.class)
                .addUserMessage(user)
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        StructuredMessage<AssessmentDraft> response;
        try {
            response = client.messages().create(params);
        } catch (RateLimitException e) {
            throw new CalendarException("The AI is busy right now. Please try again in a minute.");
        } catch (AnthropicServiceException e) {
            log.warn("Question drafting failed: HTTP {} {}", e.statusCode(), e.errorType().orElse(null));
            throw new CalendarException("The AI is unavailable right now. Please try again later.");
        } catch (RuntimeException e) {
            log.warn("Question drafting failed: {}", e.getMessage());
            throw new CalendarException("Couldn't reach the AI. Please try again.");
        }
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new CalendarException("The AI declined to write these questions. Try a different topic.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new CalendarException("That was too many questions at once. Try fewer.");
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new CalendarException("The AI didn't return any questions. Please try again."));
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? "(not given)" : s.replaceAll("[<>]", " ").strip();
    }
}
