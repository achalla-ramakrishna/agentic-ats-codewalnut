package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dev, demo and tests: nothing is sent. The API is "off" unless a test turns it on. */
public class FakeWhatsAppClient implements WhatsAppClient {

    private final List<Outgoing> sent = new ArrayList<>();
    private volatile boolean apiEnabled;
    private volatile boolean failing;

    @Override
    public boolean apiEnabled() {
        return apiEnabled;
    }

    @Override
    public synchronized String send(Outgoing message) {
        if (failing) {
            throw new CalendarException("Couldn't reach WhatsApp. Please try again.");
        }
        sent.add(message);
        return "wamid.fake-" + UUID.randomUUID();
    }

    public synchronized List<Outgoing> sent() {
        return List.copyOf(sent);
    }

    public void setApiEnabled(boolean apiEnabled) {
        this.apiEnabled = apiEnabled;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }
}
