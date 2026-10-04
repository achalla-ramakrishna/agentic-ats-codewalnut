package com.codewalnut.ats.dto;

import com.codewalnut.ats.dto.AssessmentDtos.RunCodeResult;
import com.codewalnut.ats.dto.AssessmentDtos.TestCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Live coding rooms (INT-36…). */
public final class CodingRoomDtos {

    private CodingRoomDtos() {}

    /** A built-in problem the interviewer can pick. */
    public record ProblemOption(String id, String title, String difficulty, String topic) {}

    /** Either a built-in problemId, or a title and statement (with an optional sample) typed by the interviewer. */
    public record ProblemRequest(@Size(max = 80) String problemId, @Size(max = 200) String title, @Size(max = 10_000) String statement,
            @Size(max = 5000) String sampleInput, @Size(max = 5000) String sampleOutput) {}

    public record Problem(String id, String title, String statement, String inputFormat, String outputFormat, List<TestCase> samples,
            List<String> languages, Map<String, String> starter) {}

    public record PastProblem(String title, String language, String code, Integer passed, Integer total) {}

    /** What the panel sees, updated by polling. notes: staff only. */
    public record RoomView(UUID id, UUID interviewId, String candidateName, String linkPath, String status, Problem problem, String language,
            String code, Instant codeUpdatedAt, Instant candidateSeenAt, RunCodeResult lastRun, int runCount, List<PastProblem> history,
            String notes, boolean canManage, Instant endedAt) {}

    /** room: null until someone starts one. */
    public record RoomPage(RoomView room, boolean canManage, boolean runnerAvailable, boolean candidateHasEmail) {}

    /** What the candidate sees: no notes, no history. */
    public record CandidateRoom(String status, String jobTitle, Problem problem, String language, String code, RunCodeResult lastRun,
            int runsLeft, boolean runnerAvailable) {}

    public record SaveCodeRequest(@NotBlank @Size(max = 20) String language, @NotNull @Size(max = 50_000) String code) {}

    public record NotesRequest(@Size(max = 10_000) String notes) {}
}
