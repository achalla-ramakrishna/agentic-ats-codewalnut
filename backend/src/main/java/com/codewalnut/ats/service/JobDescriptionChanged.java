package com.codewalnut.ats.service;

import java.util.UUID;

/** An opening's title or description changed: its candidates' résumés are scored again (AI-32). */
public record JobDescriptionChanged(UUID jobId) {}
