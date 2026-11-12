package com.codewalnut.ats.service;

import java.util.UUID;

/** A submitted AI interview is ready for its review (ADR-0030); handled after the submit commits. */
public record InterviewReviewRequested(UUID inviteId) {}
