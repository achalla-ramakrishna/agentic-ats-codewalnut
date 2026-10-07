package com.codewalnut.ats.domain;

/**
 * Pipeline stages, in order (PIPE-15, ADR-0024). Seven are in use: Applied / Sourced, Interviewed,
 * Shortlisted, Offer sent, Joined, On hold, Rejected. The others are retired: kept only so older
 * history still reads correctly, never offered, and mapped to the stage that replaced them
 * wherever a stage comes in ({@link #current()}).
 */
public enum Stage {
    SOURCED("Applied / Sourced", false),
    SCREENING("Screening", false),
    INTERVIEWED("Interviewed", false),
    SHORTLISTED("Shortlisted", false),
    SUBMITTED_TO_CLIENT("Submitted to client", false),
    CLIENT_INTERVIEW("Client interview", false),
    SELECTED("Selected", false),
    OFFER_SENT("Offer sent", false),
    OFFER_ACCEPTED("Offer accepted", false),
    JOINED("Joined", false),
    ON_HOLD("On hold", true),
    REJECTED("Rejected", true),
    WITHDRAWN("Withdrawn", true);

    private final String label;
    private final boolean exit;

    Stage(String label, boolean exit) {
        this.label = label;
        this.exit = exit;
    }

    public String getLabel() {
        return label;
    }

    public boolean isExit() {
        return exit;
    }

    /** Moving here needs a note saying why. */
    public boolean requiresReason() {
        return this == REJECTED || this == WITHDRAWN;
    }

    /** The stage in use that this one maps to: itself, or its replacement if retired. */
    public Stage current() {
        return switch (this) {
            case SCREENING -> SOURCED;
            case SUBMITTED_TO_CLIENT, CLIENT_INTERVIEW -> SHORTLISTED;
            case SELECTED, OFFER_ACCEPTED -> OFFER_SENT;
            case WITHDRAWN -> REJECTED;
            default -> this;
        };
    }

    public boolean isRetired() {
        return current() != this;
    }

    /** The stages in use, in pipeline order. */
    public static java.util.List<Stage> inUse() {
        return java.util.Arrays.stream(values()).filter(s -> !s.isRetired()).toList();
    }
}
