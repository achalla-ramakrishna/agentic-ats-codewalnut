package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AssistantDtos.AskRequest;
import com.codewalnut.ats.dto.AssistantDtos.AssistantStatus;
import com.codewalnut.ats.dto.AssistantDtos.PlanResponse;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AssistantService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Proposes actions only; applying them goes through the normal stage and note endpoints. */
@RestController
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/assistant/status")
    public AssistantStatus status() {
        return assistantService.status(currentUserService.require());
    }

    @PostMapping("/api/v1/jobs/{id}/assistant")
    public PlanResponse plan(@PathVariable UUID id, @Valid @RequestBody AskRequest request) {
        return assistantService.plan(currentUserService.require(), id, request.instruction());
    }
}
