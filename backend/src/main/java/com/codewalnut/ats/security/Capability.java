package com.codewalnut.ats.security;

/**
 * Coarse, role-derived permissions. Row-level rules (own jobs, own clients, assigned
 * interviews) are layered on top by {@link AccessPolicy} as each module arrives.
 */
public enum Capability {
    VIEW_DASHBOARD,
    VIEW_JOBS,
    MANAGE_JOBS,
    VIEW_CANDIDATES,
    /** Message and email candidates; take part in team chat. */
    MESSAGE_CANDIDATES,
    /** See and upload government ID documents (Aadhaar, PAN). */
    VIEW_ID_DOCUMENTS,
    /** Choose what a client sees about a candidate. */
    SHARE_WITH_CLIENTS,
    VIEW_CLIENTS,
    MANAGE_CLIENTS,
    VIEW_INTERVIEWS,
    VIEW_APPROVALS,
    VIEW_REPORTS,
    MANAGE_USERS,
    VIEW_AUDIT_LOG
}
