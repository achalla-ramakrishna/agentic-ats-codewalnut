package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarClient;
import com.codewalnut.ats.client.MailClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Whether the signed-in staff member has connected Google (Calendar + Gmail). Connecting only
 * grants access to their own account; what they may do with it is checked by each feature.
 */
@Service
@RequiredArgsConstructor
public class GoogleConnectionService {

    public record Status(boolean available, boolean calendarConnected, boolean mailConnected) {

        public boolean fullyConnected() {
            return calendarConnected && mailConnected;
        }
    }

    private final CalendarClient calendarClient;
    private final MailClient mailClient;

    public Status status() {
        CalendarClient.Status calendar = calendarClient.status();
        MailClient.Status mail = mailClient.status();
        return new Status(calendar.available() && mail.available(), calendar.connected(), mail.connected());
    }
}
