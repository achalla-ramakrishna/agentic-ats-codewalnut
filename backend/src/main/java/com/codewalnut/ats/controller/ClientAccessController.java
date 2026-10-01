package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.ClientDtos.AddContactRequest;
import com.codewalnut.ats.dto.ClientDtos.ClientContactResponse;
import com.codewalnut.ats.dto.ClientDtos.ShareRequest;
import com.codewalnut.ats.dto.ClientDtos.ShareResponse;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.ClientContactService;
import com.codewalnut.ats.service.ClientShareService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff side of client access: who at a client may sign in, and what each candidate shares. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClientAccessController {

    private final ClientContactService contactService;
    private final ClientShareService shareService;
    private final CurrentUserService currentUserService;

    @GetMapping("/clients/{id}/contacts")
    public List<ClientContactResponse> contacts(@PathVariable UUID id) {
        return contactService.list(currentUserService.require(), id);
    }

    @PostMapping("/clients/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientContactResponse addContact(@PathVariable UUID id, @Valid @RequestBody AddContactRequest request) {
        return contactService.add(currentUserService.require(), id, request.email(), request.name());
    }

    @DeleteMapping("/client-contacts/{id}")
    public ClientContactResponse removeContact(@PathVariable UUID id) {
        return contactService.remove(currentUserService.require(), id);
    }

    @GetMapping("/applications/{id}/client-share")
    public ShareResponse share(@PathVariable UUID id) {
        return shareService.get(currentUserService.require(), id);
    }

    @PutMapping("/applications/{id}/client-share")
    public ShareResponse updateShare(@PathVariable UUID id, @Valid @RequestBody ShareRequest request) {
        return shareService.share(currentUserService.require(), id, request);
    }

    @DeleteMapping("/applications/{id}/client-share")
    public ShareResponse revokeShare(@PathVariable UUID id) {
        return shareService.revoke(currentUserService.require(), id);
    }
}
