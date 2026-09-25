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
    VIEW_CLIENTS,
    MANAGE_CLIENTS,
    VIEW_INTERVIEWS,
    VIEW_APPROVALS,
    VIEW_REPORTS,
    MANAGE_USERS,
    VIEW_AUDIT_LOG
}
