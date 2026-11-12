package com.codewalnut.ats.service;

import com.codewalnut.ats.client.InterviewAgent;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.InterviewPhase;
import com.codewalnut.ats.dto.AiInterviewDtos.AreaView;
import com.codewalnut.ats.dto.AiInterviewDtos.InterviewReview;
import com.codewalnut.ats.dto.AiInterviewDtos.PhaseView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Stores and reads the AI review of an interview (ADR-0030) and works out its score. */
@Component
@RequiredArgsConstructor
public class InterviewReviews {

    /** What is kept in assessment_invite.evaluation_json. */
    public record Stored(boolean usesAi, String model, InterviewAgent.Review review) {}

    private final ObjectMapper objectMapper;

    String write(Stored stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    Stored read(AssessmentInvite invite) {
        if (invite.getEvaluationJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(invite.getEvaluationJson(), Stored.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 0–100: the area ratings weighted by importance (areas with nothing to judge are left out), with
     * the marked answers counting for a fifth when there are any. Null when nothing could be rated.
     */
    static Integer score(InterviewAgent.Review review, Integer markedPercent) {
        Map<String, Integer> ratings = ratings(review);
        double got = 0;
        int weights = 0;
        for (InterviewAgent.Area area : InterviewAgent.Area.values()) {
            Integer r = ratings.get(area.name());
            if (r != null && r > 0) {
                got += area.weight() * Math.min(5, r) / 5.0;
                weights += area.weight();
            }
        }
        if (weights == 0) {
            return markedPercent;
        }
        double rated = 100 * got / weights;
        return (int) Math.round(markedPercent == null ? rated : 0.8 * rated + 0.2 * markedPercent);
    }

    /** Area → rating, for the areas the review rated. */
    static Map<String, Integer> ratings(InterviewAgent.Review review) {
        if (review == null || review.areas() == null) {
            return Map.of();
        }
        return review.areas().stream().filter(a -> a != null && a.area() != null)
                .collect(Collectors.toMap(a -> a.area().strip().toUpperCase(java.util.Locale.ROOT),
                        a -> Math.max(0, Math.min(5, a.rating())), (x, y) -> x));
    }

    /** The staff view, or null for tests that aren't interviews. markedPercent: null when nothing was marked. */
    InterviewReview view(AssessmentInvite invite, Integer markedPercent) {
        if (invite.getEvaluation() == null) {
            return null;
        }
        Stored stored = read(invite);
        InterviewAgent.Review r = stored == null ? null : stored.review();
        List<AreaView> areas = new ArrayList<>();
        if (r != null) {
            Map<String, InterviewAgent.AreaRating> byArea = r.areas() == null ? Map.of() : r.areas().stream()
                    .filter(a -> a != null && a.area() != null)
                    .collect(Collectors.toMap(a -> a.area().strip().toUpperCase(java.util.Locale.ROOT), Function.identity(), (x, y) -> x));
            for (InterviewAgent.Area area : InterviewAgent.Area.values()) {
                InterviewAgent.AreaRating a = byArea.get(area.name());
                areas.add(new AreaView(area.name(), area.label(), area.weight(), a == null ? 0 : Math.max(0, Math.min(5, a.rating())),
                        a == null ? "Not rated." : a.reason(), a == null ? "" : a.evidence()));
            }
        }
        List<PhaseView> phases = r == null || r.phases() == null ? List.of() : r.phases().stream().filter(Objects::nonNull)
                .map(p -> {
                    InterviewPhase phase = InterviewPhase.of(p.phase());
                    return new PhaseView(p.phase(), phase == null ? p.phase() : phase.getLabel(), Math.max(0, Math.min(5, p.rating())), p.note());
                }).toList();
        return new InterviewReview(invite.getEvaluation(), stored != null && stored.usesAi(), invite.getAiScore(), markedPercent,
                areas, phases, r == null ? null : r.summary(), r == null || r.strengths() == null ? List.of() : r.strengths(),
                r == null || r.toCheck() == null ? List.of() : r.toCheck(), r == null ? null : blankToNull(r.noticePeriod()),
                r == null ? null : blankToNull(r.expectedSalary()), invite.getEvaluatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
