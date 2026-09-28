package com.codewalnut.ats.domain;

public enum DocumentKind {
    ORIGINAL_RESUME("Original résumé"),
    CODEWALNUT_RESUME("CodeWalnut résumé");

    private final String label;

    DocumentKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
