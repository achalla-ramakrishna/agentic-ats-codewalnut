package com.codewalnut.ats.client;

/** The user needs to connect (or reconnect) their Google Calendar first. */
public class CalendarNotConnectedException extends RuntimeException {

    public CalendarNotConnectedException() {
        super("Connect your Google Calendar first");
    }
}
