package com.codewalnut.ats.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Interview self-booking (PIPE-24). */
public final class BookingDtos {

    private BookingDtos() {}

    /** A daily window, e.g. 10:00–13:00, cut into slots of the chosen length. */
    public record Window(@NotNull @Pattern(regexp = "\\d{2}:\\d{2}") String start, @NotNull @Pattern(regexp = "\\d{2}:\\d{2}") String end) {}

    public record CreateSlotsRequest(
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            boolean weekdaysOnly,
            @NotEmpty @Size(max = 6) List<@Valid Window> windows,
            @NotNull @Min(15) @Max(120) Integer durationMinutes,
            @NotBlank @Size(max = 64) String timeZone,
            @Size(max = 10) List<@Size(max = 254) String> interviewerEmails) {}

    /** candidateName/applicationId: who booked it; interviewId: invite sent. */
    public record SlotView(UUID id, Instant startAt, Instant endAt, String timeZone, UUID applicationId, String candidateName,
            Instant bookedAt, UUID interviewId) {}

    public record SlotsResponse(List<SlotView> slots, int free, int booked, int scheduled) {}

    public record CreateSlotsResult(int created, int skipped, SlotsResponse slots) {}

    public record LinksRequest(@NotEmpty @Size(max = 500) List<UUID> applicationIds) {}

    /** links: applicationId → their booking link; problems: why some have none. */
    public record LinksResponse(Map<UUID, String> links, Map<UUID, String> problems) {}

    public record SendInvitesResult(int scheduled, List<String> failed) {}

    // ---- public (the candidate's booking page) ----

    public record PublicSlot(UUID id, Instant startAt, Instant endAt) {}

    /** booked: their slot, if any; scheduled: the calendar invite has been sent. */
    public record BookingPage(String firstName, String jobTitle, String timeZone, int durationMinutes, PublicSlot booked,
            boolean scheduled, List<PublicSlot> slots) {}

    public record BookRequest(@NotNull UUID slotId) {}
}
