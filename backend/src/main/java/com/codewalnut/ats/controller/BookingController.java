package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.BookingDtos.BookRequest;
import com.codewalnut.ats.dto.BookingDtos.BookingPage;
import com.codewalnut.ats.dto.BookingDtos.CreateSlotsRequest;
import com.codewalnut.ats.dto.BookingDtos.CreateSlotsResult;
import com.codewalnut.ats.dto.BookingDtos.LinksRequest;
import com.codewalnut.ats.dto.BookingDtos.LinksResponse;
import com.codewalnut.ats.dto.BookingDtos.SendInvitesResult;
import com.codewalnut.ats.dto.BookingDtos.SlotsResponse;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.BookingService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Interview self-booking (PIPE-24): staff manage slots and links; candidates book with their link. */
@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService service;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/jobs/{id}/slots")
    public SlotsResponse slots(@PathVariable UUID id) {
        return service.slots(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/jobs/{id}/slots")
    public CreateSlotsResult create(@PathVariable UUID id, @Valid @RequestBody CreateSlotsRequest request) {
        return service.createSlots(currentUserService.require(), id, request);
    }

    @DeleteMapping("/api/v1/slots/{id}")
    public SlotsResponse delete(@PathVariable UUID id) {
        return service.deleteSlot(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/jobs/{id}/slots/send-invites")
    public SendInvitesResult sendInvites(@PathVariable UUID id) {
        return service.sendInvites(currentUserService.require(), id);
    }

    @PostMapping("/api/v1/booking-links")
    public LinksResponse links(@Valid @RequestBody LinksRequest request) {
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().path("/").toUriString();
        return service.links(currentUserService.require(), request.applicationIds(), base);
    }

    // Public: the candidate's link is the only key.

    @GetMapping("/api/v1/public/booking/{token}")
    public BookingPage page(@PathVariable String token) {
        return service.page(token);
    }

    @PostMapping("/api/v1/public/booking/{token}")
    public BookingPage book(@PathVariable String token, @Valid @RequestBody BookRequest request) {
        return service.book(token, request.slotId());
    }
}
