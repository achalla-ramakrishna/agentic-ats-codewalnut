package com.codewalnut.ats.domain;

import lombok.Getter;

/** The phases of an AI interview (ADR-0030), in order; stored as the question's section. */
@Getter
public enum InterviewPhase {
    INTRO("Introduction"),
    EXPERIENCE("Work experience"),
    SKILLS("Skills"),
    PROJECTS("Projects"),
    ARCHITECTURE("Architecture"),
    TECHNICAL("Technical round"),
    SOFT_SKILLS("Soft skills"),
    READINESS("Readiness to join");

    private final String label;

    InterviewPhase(String label) {
        this.label = label;
    }

    public static InterviewPhase of(String section) {
        if (section == null) {
            return null;
        }
        for (InterviewPhase p : values()) {
            if (p.name().equals(section)) {
                return p;
            }
        }
        return null;
    }
}
