package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.InterviewGuideDtos.InterviewGuide;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.InterviewGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Interview questions for staff who interview (INT-21). */
@RestController
@RequiredArgsConstructor
public class InterviewGuideController {

    private final InterviewGuideService interviewGuideService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/interview-guide")
    public InterviewGuide guide() {
        return interviewGuideService.guide(currentUserService.require());
    }
}
