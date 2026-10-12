package com.codewalnut.ats.domain;

public enum ApplicationEventType {
    CREATED,
    STAGE_CHANGED,
    NOTE,
    INTERVIEW_SCHEDULED,
    INTERVIEW_CANCELLED,
    /** Rescheduled (kept short: the column is VARCHAR(20)). */
    INTERVIEW_MOVED,
    EMAIL_SENT,
    DOCS_REQUESTED,
    DOC_UPLOADED,
    SHARED_WITH_CLIENT,
    WHATSAPP_SENT,
    TEST_SENT,
    TEST_SUBMITTED,
    /** A call, WhatsApp or meeting outside the app, logged by a recruiter. */
    CONTACT_LOGGED,
    /** The candidate picked an interview slot from their booking link (PIPE-24). */
    SLOT_BOOKED
}
