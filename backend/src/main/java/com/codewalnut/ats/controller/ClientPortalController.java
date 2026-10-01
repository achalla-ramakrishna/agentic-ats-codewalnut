package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.ClientDtos.ClientCandidate;
import com.codewalnut.ats.dto.ClientDtos.ClientMe;
import com.codewalnut.ats.dto.MessageDtos.CandidateMessageResponse;
import com.codewalnut.ats.dto.MessageDtos.CandidatePostRequest;
import com.codewalnut.ats.security.CurrentClientService;
import com.codewalnut.ats.service.ClientPortalService;
import com.codewalnut.ats.service.MessageService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Client contacts only: what CodeWalnut shared with their company. */
@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
public class ClientPortalController {

    private final CurrentClientService currentClientService;
    private final ClientPortalService portalService;
    private final MessageService messageService;

    @GetMapping("/me")
    public ClientMe me() {
        return portalService.me(currentClientService.require());
    }

    @GetMapping("/candidates")
    public List<ClientCandidate> candidates() {
        return portalService.candidates(currentClientService.require());
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean inline) {
        return DocumentController.file(portalService.download(currentClientService.require(), id), inline);
    }

    @GetMapping("/applications/{id}/messages")
    public List<CandidateMessageResponse> messages(@PathVariable UUID id) {
        return messageService.clientThread(currentClientService.require(), id);
    }

    @PostMapping("/applications/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateMessageResponse post(@PathVariable UUID id, @Valid @RequestBody CandidatePostRequest request) {
        return messageService.clientPost(currentClientService.require(), id, request.body());
    }
}
