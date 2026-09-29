package com.codewalnut.ats.domain;

public enum WorkMode {
    ONSITE("Office"),
    HYBRID("Hybrid"),
    REMOTE("Remote");

    private final String label;

    WorkMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
