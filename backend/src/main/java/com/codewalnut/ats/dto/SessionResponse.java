package com.codewalnut.ats.dto;

import com.codewalnut.ats.security.SessionType;

/**
 * Who is signed in, if anyone. type is null when signed out. viewAs is set while an admin views the
 * app as someone else (ADR-0013); type is then that person's kind of session.
 */
public record SessionResponse(SessionType type, ViewAsDtos.Info viewAs) {

    public SessionResponse(SessionType type) {
        this(type, null);
    }
}
