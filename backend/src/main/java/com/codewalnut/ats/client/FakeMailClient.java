package com.codewalnut.ats.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stand-in mailer for dev, demo and tests: nothing leaves the app. */
public class FakeMailClient implements MailClient {

    private final List<Email> sent = new ArrayList<>();
    private volatile boolean connected = true;
    private volatile boolean failing;

    @Override
    public Status status() {
        return new Status(true, connected);
    }

    @Override
    public synchronized String send(Email email) {
        if (!connected) {
            throw new CalendarNotConnectedException();
        }
        if (failing) {
            throw new CalendarException("Couldn't reach Gmail. Nothing was sent; please try again.");
        }
        MimeMessages.plainText(email); // same validation as the real one
        sent.add(email);
        return "fake-" + UUID.randomUUID();
    }

    public synchronized List<Email> sent() {
        return List.copyOf(sent);
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }
}
