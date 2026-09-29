package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Interview;
import com.codewalnut.ats.domain.InterviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InterviewDtos {

    private InterviewDtos() {}

    public record ScheduleInterviewRequest(
            @Size(max = 200) String title,
            @NotNull Instant startAt,
            @NotNull @Min(15) @Max(480) Integer durationMinutes,
            @NotBlank @Size(max = 64) String timeZone,
            @Size(max = 10) List<@Size(max = 254) String> interviewerEmails,
            @Size(max = 5000) String message) {}

    public record CancelInterviewRequest(@Size(max = 500) String reason) {}

    public record InterviewResponse(
            UUID id, UUID applicationId, UUID jobId, String jobTitle, UUID candidateId, String candidateName,
            String title, Instant startAt, Instant endAt, String timeZone, List<String> interviewers,
            String message, InterviewStatus status, String meetLink, String calendarLink, String organizerEmail,
            String cancelReason, Instant createdAt) {

        public static InterviewResponse from(Interview i) {
            var a = i.getApplication();
            return new InterviewResponse(i.getId(), a.getId(), a.getJob().getId(), a.getJob().getTitle(),
                    a.getCandidate().getId(), a.getCandidate().getName(), i.getTitle(), i.getStartAt(), i.getEndAt(),
                    i.getTimeZone(), i.interviewers(), i.getMessage(), i.getStatus(), i.getMeetLink(),
                    i.getCalendarLink(), i.getOrganizerEmail(), i.getCancelReason(), i.getCreatedAt());
        }
    }

    /** What a candidate sees about their own interviews. */
    public record CandidateInterviewResponse(
            String jobTitle, String title, Instant startAt, Instant endAt, String timeZone, String meetLink) {

        public static CandidateInterviewResponse from(Interview i) {
            return new CandidateInterviewResponse(i.getApplication().getJob().getTitle(), i.getTitle(),
                    i.getStartAt(), i.getEndAt(), i.getTimeZone(), i.getMeetLink());
        }
    }
}
