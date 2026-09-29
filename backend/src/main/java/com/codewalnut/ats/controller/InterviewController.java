package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.InterviewDtos.CancelInterviewRequest;
import com.codewalnut.ats.dto.InterviewDtos.InterviewResponse;
import com.codewalnut.ats.dto.InterviewDtos.ScheduleInterviewRequest;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.InterviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
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
}
