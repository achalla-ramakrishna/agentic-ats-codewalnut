package com.codewalnut.ats.domain;

public enum HiringType {
    /** CodeWalnut hires for itself. */
    INTERNAL("Internal"),
    /** On CodeWalnut payroll, working for a client. */
    CLIENT_DEPLOYED("Client – CodeWalnut payroll"),
    /** On the client's payroll. */
    DIRECT_PLACEMENT("Client – direct placement");

    private final String label;

    HiringType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
