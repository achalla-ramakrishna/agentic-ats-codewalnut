package com.codewalnut.ats.service;

import java.util.UUID;

/** A new original résumé was stored for this candidate (not from a bulk upload, which reads it itself). */
public record ResumeAddedEvent(UUID candidateId) {}
