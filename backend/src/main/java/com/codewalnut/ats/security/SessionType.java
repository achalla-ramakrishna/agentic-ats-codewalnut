package com.codewalnut.ats.security;

/** Who a session belongs to. Stored as a granted authority on the session. */
public enum SessionType {
    STAFF,
    CANDIDATE,
    /** A person at a client company (e.g. Blend's hiring manager). */
    CLIENT;

    public String authority() {
        return "ROLE_" + name();
    }
}
