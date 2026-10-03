package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Stage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AssistantDtos {

    private AssistantDtos() {}

    public record AskRequest(@NotBlank @Size(max = 2000) String instruction) {}

    public record AssistantStatus(boolean available) {}

    /**
     * A proposal checked against the opening: every id belongs to it and every stage exists.
     * Nothing has been changed yet; the person applies it (ADR-0009).
     */
    public record PlanResponse(
            String instruction, String summary, List<ProposedAction> actions, List<Unresolved> unresolved,
            List<String> notes, boolean aiGenerated, String answer, List<Match> matches) {}

    /** A candidate an answer points to; fitPercent from their résumé reading, if any. */
    public record Match(UUID applicationId, String name, String stageLabel, Integer fitPercent, String reason) {}

    /** needsReason: moving here requires a reason and none was given. */
    public record ProposedAction(
            String type, UUID applicationId, String candidateName, Stage fromStage, String fromLabel, Stage toStage,
            String toLabel, String note, boolean needsReason) {}

    public record Option(UUID applicationId, String name, String stageLabel) {}

    public record Unresolved(String mention, List<Option> options, Stage toStage, String toLabel, String note) {}
}
