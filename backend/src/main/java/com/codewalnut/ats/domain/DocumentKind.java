package com.codewalnut.ats.domain;

/**
 * Kinds of candidate documents. Sensitive kinds (government IDs) are visible only to people with
 * {@code VIEW_ID_DOCUMENTS}. Candidates may upload every kind except the CodeWalnut résumé.
 */
public enum DocumentKind {
    ORIGINAL_RESUME("Original résumé", false, true, false),
    CODEWALNUT_RESUME("CodeWalnut résumé", false, false, false),
    AADHAAR("Aadhaar card (masked)", true, true, true),
    PAN("PAN card", true, true, true),
    DEGREE_CERTIFICATE("Degree certificate / marksheet", false, true, true),
    PHOTO("Passport-size photo", false, true, true),
    OTHER("Other document", false, true, true);

    private final String label;
    private final boolean sensitive;
    private final boolean candidateUploadable;
    private final boolean imagesAllowed;

    DocumentKind(String label, boolean sensitive, boolean candidateUploadable, boolean imagesAllowed) {
        this.label = label;
        this.sensitive = sensitive;
        this.candidateUploadable = candidateUploadable;
        this.imagesAllowed = imagesAllowed;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public boolean isCandidateUploadable() {
        return candidateUploadable;
    }

    public boolean isImagesAllowed() {
        return imagesAllowed;
    }
}
