package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.BankQuestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The question bank and the test-paper builder (ADR-0014). */
public final class BankDtos {

    private BankDtos() {}

    public record BankQuestionView(
            UUID id, Assessment.Category area, BankQuestion.Section section, String sectionLabel, String topic,
            BankQuestion.Difficulty difficulty, AssessmentQuestion.Kind kind, String prompt, String code, String figure,
            List<String> options, List<String> optionFigures, List<Integer> correct, List<String> acceptedAnswers, int points,
            String explanation, BankQuestion.Source source, BankQuestion.Status status, int timesUsed) {}

    /**
     * counts: active questions per section and difficulty, for the builder. topics: per section.
     * guide: every topic with what it covers and how many active questions it has at each level.
     */
    public record BankOverview(List<Count> counts, List<TopicCount> topics, List<Preset> presets, List<TopicGuide> guide) {}

    /** A topic in the guide; covers and example are empty for topics added by hand. */
    public record TopicGuide(String id, BankQuestion.Section section, String sectionLabel, String name, String covers, String example,
            long easy, long medium, long hard) {}

    public record Count(Assessment.Category area, BankQuestion.Section section, String sectionLabel,
            BankQuestion.Difficulty difficulty, long count) {}

    public record TopicCount(BankQuestion.Section section, String topic, long count, long withPictures) {}

    public record BankPage(List<BankQuestionView> items, int total) {}

    public record BankQuestionRequest(
            @NotNull Assessment.Category area,
            @NotNull BankQuestion.Section section,
            @NotBlank @Size(max = 60) String topic,
            @NotNull BankQuestion.Difficulty difficulty,
            @NotNull @Valid AssessmentDtos.QuestionRequest question) {}

    public record BankDraftRequest(
            @NotNull BankQuestion.Section section,
            @NotBlank @Size(max = 60) String topic,
            @NotNull BankQuestion.Difficulty difficulty,
            @Min(1) @Max(10) int count) {}

    public record BankDraftResult(int added, List<String> notes, List<BankQuestionView> questions) {}

    /** How many questions of each difficulty to take from a section. */
    public record SectionPlan(@NotNull BankQuestion.Section section, @Min(0) @Max(60) int easy, @Min(0) @Max(60) int medium,
            @Min(0) @Max(60) int hard) {}

    /** How many questions of each difficulty to take from one topic. */
    public record TopicPlan(@NotNull BankQuestion.Section section, @NotBlank @Size(max = 60) String topic, @Min(0) @Max(30) int easy,
            @Min(0) @Max(30) int medium, @Min(0) @Max(30) int hard) {}

    /**
     * EASY_FIRST: easy → hard across the paper; HARD_FIRST: hard → easy; BY_SECTION: section by section
     * (topic by topic when built from topics), easy → hard in each; SHUFFLED.
     */
    public enum Order { EASY_FIRST, HARD_FIRST, BY_SECTION, SHUFFLED }

    public record BuildRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull Assessment.Category area,
            @Min(5) @Max(180) int durationMinutes,
            @Min(0) @Max(100) int passPercent,
            @Size(max = 10) List<@Valid SectionPlan> sections,
            @Size(max = 40) List<@Valid TopicPlan> topics,
            @NotNull Order order) {}

    /** A ready-made blueprint, e.g. "TCS NQT style". */
    public record Preset(String id, String name, String description, int durationMinutes, int passPercent, List<SectionPlan> sections) {}

    public record AddFromBankRequest(@NotEmpty @Size(max = 60) List<UUID> questionIds) {}
}
