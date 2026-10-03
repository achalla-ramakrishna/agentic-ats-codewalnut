package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.BankQuestion;
import java.util.List;

/**
 * One question of the built-in bank. code: a snippet shown under the prompt, or null. correct:
 * option indexes (choice questions); accepted: answers for a short answer. optionFigures: one
 * picture per option, or null when options are text.
 */
public record Seed(
        String key, Assessment.Category area, BankQuestion.Section section, String topic, BankQuestion.Difficulty difficulty,
        AssessmentQuestion.Kind kind, String prompt, String code, String figure, List<String> options, List<String> optionFigures,
        List<Integer> correct, List<String> accepted, String explanation) {

    public int points() {
        return switch (difficulty) {
            case EASY -> 1;
            case MEDIUM -> 2;
            case HARD -> 3;
        };
    }
}
