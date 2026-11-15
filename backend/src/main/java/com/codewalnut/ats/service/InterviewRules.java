package com.codewalnut.ats.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How spoken questions of AI interviews are answered (AINT-24, ADR-0031): seconds to read a question
 * before recording starts, recordings allowed per question, and after how many answers that sound read
 * out the team is told. Configuration, not code: ats.interview.* in application.yml.
 */
@Component
public class InterviewRules {

    private final int thinkSeconds;
    private final int takes;
    private final int readingAlertAfter;

    public InterviewRules(@Value("${ats.interview.think-seconds:30}") int thinkSeconds, @Value("${ats.interview.takes:2}") int takes,
            @Value("${ats.interview.reading-alert-after:1}") int readingAlertAfter) {
        this.thinkSeconds = Math.max(5, Math.min(thinkSeconds, 300));
        this.takes = Math.max(1, Math.min(takes, 5));
        this.readingAlertAfter = Math.max(1, readingAlertAfter);
    }

    public int thinkSeconds() {
        return thinkSeconds;
    }

    public int takes() {
        return takes;
    }

    public int readingAlertAfter() {
        return readingAlertAfter;
    }
}
