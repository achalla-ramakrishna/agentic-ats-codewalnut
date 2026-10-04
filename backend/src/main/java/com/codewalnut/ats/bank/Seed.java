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
        List<Integer> correct, List<String> accepted, String explanation, String codingJson, String testsJson) {

    /** A multiple-choice or short-answer question. */
    public Seed(String key, Assessment.Category area, BankQuestion.Section section, String topic, BankQuestion.Difficulty difficulty,
            AssessmentQuestion.Kind kind, String prompt, String code, String figure, List<String> options, List<String> optionFigures,
            List<Integer> correct, List<String> accepted, String explanation) {
        this(key, area, section, topic, difficulty, kind, prompt, code, figure, options, optionFigures, correct, accepted, explanation, null, null);
    }

    /** 1/2/3 by difficulty; coding problems (codingJson: public spec, testsJson: hidden tests) 5/10/15. */
    public int points() {
        int base = switch (difficulty) {
            case EASY -> 1;
            case MEDIUM -> 2;
            case HARD -> 3;
        };
        return kind == AssessmentQuestion.Kind.CODING ? base * 5 : base;
    }
}
