package com.codewalnut.ats.client;

import java.time.Instant;
import java.util.List;

/**
 * Creates and cancels interview events on the signed-in staff member's calendar. The only way
 * the app talks to a calendar provider (docs/features/interviews-and-scorecards.md, ADR-0005).
 */
public interface CalendarClient {

    /** {@code available}: calendar is set up for this deployment; {@code connected}: this user has granted access. */
    record Status(boolean available, boolean connected) {}

    /** Attendees receive the provider's invitation email, with the video link. */
    record Invite(String title, String description, Instant start, Instant end, String timeZone, List<String> attendees) {}

    record Event(String id, String meetLink, String htmlLink) {}

    Status status();

    /** @throws CalendarNotConnectedException when the user hasn't connected (or access expired) */
    Event create(Invite invite);

    /**
     * Moves an existing event (new time, title, attendees) and notifies attendees. The video link
     * stays the same.
     */
    Event update(String eventId, Invite invite);

    /** Cancels the event and notifies attendees. An event that no longer exists counts as cancelled. */
    void cancel(String eventId);
}
