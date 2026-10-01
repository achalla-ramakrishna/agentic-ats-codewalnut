package com.codewalnut.ats.domain;

public enum MessageChannel {
    /** Staff and the candidate; the candidate sees it in their area and may get it by email. */
    CANDIDATE,
    /** Internal team discussion about the candidate; never shown to the candidate. */
    TEAM,
    /** Staff and the client's contacts about a candidate shared with that client; never shown to the candidate. */
    CLIENT
}
