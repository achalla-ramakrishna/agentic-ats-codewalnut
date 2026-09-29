package com.codewalnut.ats.client;

/** The calendar provider refused or failed; the message is safe to show to staff. */
public class CalendarException extends RuntimeException {

    public CalendarException(String message) {
        super(message);
    }
}
