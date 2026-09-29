package com.codewalnut.ats.client;

/** The user needs to connect (or reconnect) Google (Calendar and Gmail) first. */
public class CalendarNotConnectedException extends RuntimeException {

    public CalendarNotConnectedException() {
        super("Connect your Google account (Calendar and Gmail) first");
    }
}
