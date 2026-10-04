package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.InterviewKitDtos.GenerateKitRequest;
import com.codewalnut.ats.dto.InterviewKitDtos.KitPage;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.InterviewKitService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InterviewKitController {

    private final InterviewKitService kitService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/jobs/{id}/interview-kit")
    public KitPage kit(@PathVariable UUID id) {
        return kitService.page(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/jobs/{id}/interview-kit")
    public KitPage generate(@PathVariable UUID id, @Valid @RequestBody(required = false) GenerateKitRequest request) {
        return kitService.generate(currentUserService.require(), id, request);
    }
}
