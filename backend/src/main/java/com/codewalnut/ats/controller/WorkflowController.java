package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.WorkflowDtos.LogContactRequest;
import com.codewalnut.ats.dto.WorkflowDtos.Timeline;
import com.codewalnut.ats.dto.WorkflowDtos.WorkflowBoard;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.WorkflowService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The workflow view (WF-01…): contacts, what happened, next steps, and one candidate's timeline. */
@RestController
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/workflow")
    public WorkflowBoard board(@RequestParam(required = false) UUID jobId) {
        return workflowService.board(currentUserService.require(), jobId);
    }

    @GetMapping("/api/v1/applications/{id}/timeline")
    public Timeline timeline(@PathVariable UUID id) {
        return workflowService.timeline(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/applications/{id}/contacts")
    public Timeline logContact(@PathVariable UUID id, @Valid @RequestBody LogContactRequest request) {
        return workflowService.logContact(currentUserService.require(), id, request.how(), request.note());
    }
}
