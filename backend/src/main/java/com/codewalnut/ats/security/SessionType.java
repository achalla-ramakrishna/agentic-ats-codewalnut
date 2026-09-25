package com.codewalnut.ats.security;

/** Who a session belongs to. Stored as a granted authority on the session. */
public enum SessionType {
    STAFF,
    CANDIDATE;

    public String authority() {
        return "ROLE_" + name();
    }
}
