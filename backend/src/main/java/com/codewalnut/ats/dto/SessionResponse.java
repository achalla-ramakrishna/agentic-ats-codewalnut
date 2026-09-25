package com.codewalnut.ats.dto;

import com.codewalnut.ats.security.SessionType;

/** Who is signed in, if anyone. type is null when signed out. */
public record SessionResponse(SessionType type) {}
