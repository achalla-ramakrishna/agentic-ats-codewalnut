package com.codewalnut.ats.domain;

/**
 * Pipeline stages, in order. The last three are exits. Kept deliberately simple for the first
 * release; per-job stage templates come later (docs/features/pipeline.md).
 */
public enum Stage {
    SOURCED("Sourced", false),
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
}
