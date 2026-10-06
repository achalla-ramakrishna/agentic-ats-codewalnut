package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.AskDtos.ActionOutcome;
import com.codewalnut.ats.dto.AskDtos.ActionRequest;
import com.codewalnut.ats.dto.AskDtos.AskRequest;
import com.codewalnut.ats.dto.AskDtos.AskStatus;
import com.codewalnut.ats.dto.AskDtos.Conversation;
import com.codewalnut.ats.dto.AskDtos.ConversationSummary;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AskService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Ask ATS (ASK-01…): staff chats with the ATS assistant. Each person sees only their own chats. */
@RestController
@RequiredArgsConstructor
public class AskController {

    private final AskService askService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/ask/status")
    public AskStatus status() {
        return askService.status(currentUserService.require());
    }

    @GetMapping("/api/v1/ask/conversations")
    public List<ConversationSummary> conversations() {
        return askService.list(currentUserService.require());
    }

    @GetMapping("/api/v1/ask/conversations/{id}")
    public Conversation conversation(@PathVariable UUID id) {
        return askService.get(currentUserService.require(), id);
    }

    @DeleteMapping("/api/v1/ask/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        askService.delete(currentUserService.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/ask/conversations/{id}/actions/{actionId}")
    public ActionOutcome decide(@PathVariable UUID id, @PathVariable String actionId, @Valid @RequestBody ActionRequest request) {
        String portal = ServletUriComponentsBuilder.fromCurrentContextPath().path("/").toUriString();
        return askService.decide(currentUserService.require(), id, actionId, request.decision(), request.subject(), request.body(), portal);
    }

    @PostMapping("/api/v1/ask")
    public Conversation ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(currentUserService.require(), request.conversationId(), request.question());
    }
}
