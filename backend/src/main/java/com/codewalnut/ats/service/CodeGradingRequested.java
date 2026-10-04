package com.codewalnut.ats.service;

import java.util.UUID;

/** A submitted test has coding answers to run; graded after the submit commits (ADR-0016). */
public record CodeGradingRequested(UUID inviteId) {}
