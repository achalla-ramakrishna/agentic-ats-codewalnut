package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.FeedbackDtos.FeedbackRequest;
import com.codewalnut.ats.dto.FeedbackDtos.FeedbackSummary;
import com.codewalnut.ats.dto.FeedbackDtos.InterviewFeedbackPage;
import com.codewalnut.ats.dto.FeedbackDtos.RecentInterview;
import com.codewalnut.ats.dto.InterviewDtos.CancelInterviewRequest;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import com.codewalnut.ats.dto.InterviewDtos.LogInterviewRequest;
import com.codewalnut.ats.dto.InterviewDtos.ScheduleInterviewRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.InterviewFeedbackService;
import com.codewalnut.ats.service.InterviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewFeedbackService feedbackService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/interviews")
    public List<InterviewResponse> upcoming() {
        return interviewService.upcoming(currentUserService.require());
    }

    @GetMapping("/api/v1/applications/{id}/interviews")
    public List<InterviewResponse> forApplication(@PathVariable UUID id) {
        return interviewService.forApplication(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/applications/{id}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse schedule(@PathVariable UUID id, @Valid @RequestBody ScheduleInterviewRequest request) {
        return interviewService.schedule(currentUserService.require(), id, request);
    }

    @PostMapping("/api/v1/interviews/{id}/cancel")
    public InterviewResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelInterviewRequest request) {
        return interviewService.cancel(currentUserService.require(), id, request == null ? null : request.reason());
    }

    @GetMapping("/api/v1/interviews/recent")
    public List<RecentInterview> recent() {
        return feedbackService.recent(currentUserService.require());
    }

    @PostMapping("/api/v1/applications/{id}/interviews/log")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse log(@PathVariable UUID id, @Valid @RequestBody LogInterviewRequest request) {
        return interviewService.log(currentUserService.require(), id, request);
    }

    @GetMapping("/api/v1/interviews/feedback-due")
    public List<InterviewResponse> feedbackDue() {
        return feedbackService.due(currentUserService.require());
    }

    @GetMapping("/api/v1/interviews/{id}/feedback")
    public InterviewFeedbackPage feedback(@PathVariable UUID id) {
        return feedbackService.page(currentUserService.require(), id);
    }

    @PutMapping("/api/v1/interviews/{id}/feedback")
    public InterviewFeedbackPage submitFeedback(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.submit(currentUserService.require(), id, request);
    }

    @GetMapping("/api/v1/applications/{id}/interview-feedback")
    public List<FeedbackSummary> feedbackSummaries(@PathVariable UUID id) {
        return feedbackService.summaries(currentUserService.require(), id);
    }
}
