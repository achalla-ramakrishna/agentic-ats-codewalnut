package com.codewalnut.ats.domain;

/** Staff roles. Client reviewers and candidates are external and are not AppUsers. */
public enum Role {
    ADMIN("Admin"),
    RECRUITER("Recruiter"),
    HIRING_MANAGER("Hiring Manager"),
    ACCOUNT_MANAGER("Account Manager"),
    INTERVIEWER("Interviewer"),
    APPROVER("Approver");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
