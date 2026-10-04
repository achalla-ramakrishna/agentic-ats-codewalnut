package com.codewalnut.ats.dto;

import com.codewalnut.ats.dto.BankDtos.Preset;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Interview kits (INT-28…): staff only; never shown to candidates or clients. */
public final class InterviewKitDtos {

    private InterviewKitDtos() {}

    /** A skill found in the job description. mustHave: false when it only appears as "nice to have". */
    public record KitSkill(String name, String area, int mentions, boolean mustHave) {}

    /** The online test: a role test at the kit's level. */
    public record KitTest(String roleId, String roleName, String level, String levelLabel, String years, List<String> areas,
            Preset preset) {}

    /** source: "job" for questions written from the job description, otherwise the guide category's name. */
    public record KitQuestion(String topic, String question, String strong, String redFlags, String source) {}

    public record KitCoding(String id, String title, String difficulty, String topic, String statement, String input, String output,
            String sampleInput, String sampleOutput, String approach, String lookFor) {}

    public record KitRound(String name, int minutes, String who, String purpose, List<KitQuestion> questions, List<KitCoding> coding) {}

    public record ScoreRow(String name, String guidance) {}

    /** What is stored: everything generated from the job description at one point in time. */
    public record KitContent(String level, String levelLabel, String detectedLevel, String roleId, String roleName,
            String detectedRoleId, List<KitSkill> skills, List<String> notes, KitTest test, List<KitRound> rounds,
            List<ScoreRow> scorecard, String hireBar, long seed) {}

    /** outdated: the job's title or description changed after the kit was generated. */
    public record KitView(UUID jobId, String jobTitle, String clientName, KitContent kit, String generatedBy, Instant generatedAt,
            boolean outdated) {}

    public record Option(String id, String label) {}

    /** kit: null until someone generates it. */
    public record KitPage(UUID jobId, String jobTitle, String clientName, boolean hasDescription, KitView kit, boolean canGenerate,
            List<Option> levels, List<Option> roles) {}

    /** Optional overrides of what was detected; null means "use what the job description says". */
    public record GenerateKitRequest(@Size(max = 20) String level, @Size(max = 60) String roleId) {}
}
