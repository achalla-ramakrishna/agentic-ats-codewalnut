package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.dto.MessageDtos.CandidateMessageResponse;
import com.codewalnut.ats.dto.MessageDtos.CandidatePostRequest;
import com.codewalnut.ats.dto.MessageDtos.InboxItem;
import com.codewalnut.ats.dto.MessageDtos.MessageResponse;
import com.codewalnut.ats.dto.MessageDtos.PostMessageRequest;
import com.codewalnut.ats.dto.MessageDtos.WhatsAppStatusResponse;
import com.codewalnut.ats.security.CurrentCandidateService;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.MessageService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final CurrentUserService currentUserService;
    private final CurrentCandidateService currentCandidateService;

    @GetMapping("/api/v1/applications/{id}/messages")
    public List<MessageResponse> thread(@PathVariable UUID id, @RequestParam MessageChannel channel) {
        return messageService.thread(currentUserService.require(), id, channel);
    }

    @PostMapping("/api/v1/applications/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse post(@PathVariable UUID id, @Valid @RequestBody PostMessageRequest request) {
        String portal = ServletUriComponentsBuilder.fromCurrentContextPath().path("/").toUriString();
        return messageService.post(currentUserService.require(), id, request, portal);
    }

    @GetMapping("/api/v1/whatsapp/status")
    public WhatsAppStatusResponse whatsAppStatus() {
        return messageService.whatsAppStatus(currentUserService.require());
    }

    @GetMapping("/api/v1/messages/inbox")
    public List<InboxItem> inbox() {
        return messageService.inbox(currentUserService.require());
    }

    @GetMapping("/api/v1/candidate/applications/{id}/messages")
    public List<CandidateMessageResponse> candidateThread(@PathVariable UUID id) {
        return messageService.candidateThread(currentCandidateService.require(), id);
    }

    @PostMapping("/api/v1/candidate/applications/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateMessageResponse candidatePost(@PathVariable UUID id, @Valid @RequestBody CandidatePostRequest request) {
        return messageService.candidatePost(currentCandidateService.require(), id, request.body());
    }
}
