package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stand-in calendar for the dev and demo profiles and for tests: nothing leaves the app, and
 * the "Meet" links are obviously fake. Tests can inspect what was sent and simulate failures.
 */
public class FakeCalendarClient implements CalendarClient {

    private final List<Invite> created = new ArrayList<>();
    private final List<String> cancelled = new ArrayList<>();
    private volatile boolean connected = true;
    private volatile boolean failing;

    @Override
    public Status status() {
        return new Status(true, connected);
    }

    @Override
    public synchronized Event create(Invite invite) {
        check();
        created.add(invite);
        String id = "fake-" + UUID.randomUUID();
        return new Event(id, "https://example.com/fake-meet/" + id.substring(5, 13), null);
    }

    @Override
    public synchronized void cancel(String eventId) {
        check();
        cancelled.add(eventId);
    }

    private void check() {
        if (!connected) {
            throw new CalendarNotConnectedException();
        }
        if (failing) {
            throw new CalendarException("Couldn't reach Google Calendar. Nothing was sent; please try again.");
        }
    }

    public synchronized List<Invite> created() {
        return List.copyOf(created);
    }

    public synchronized List<String> cancelled() {
        return List.copyOf(cancelled);
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }
}
