package com.codewalnut.ats.security;

import static com.codewalnut.ats.security.Capability.MANAGE_CLIENTS;
import static com.codewalnut.ats.security.Capability.MANAGE_JOBS;
import static com.codewalnut.ats.security.Capability.VIEW_APPROVALS;
import static com.codewalnut.ats.security.Capability.VIEW_CANDIDATES;
import static com.codewalnut.ats.security.Capability.VIEW_CLIENTS;
import static com.codewalnut.ats.security.Capability.VIEW_DASHBOARD;
import static com.codewalnut.ats.security.Capability.VIEW_INTERVIEWS;
import static com.codewalnut.ats.security.Capability.VIEW_JOBS;
import static com.codewalnut.ats.security.Capability.VIEW_REPORTS;

import com.codewalnut.ats.domain.Role;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The role → capability matrix from docs/SPEC.md ("Users & roles"). */
public final class RolePermissions {

    private static final Map<Role, Set<Capability>> MATRIX = new EnumMap<>(Role.class);

    static {
        MATRIX.put(Role.ADMIN, EnumSet.allOf(Capability.class));
        MATRIX.put(Role.RECRUITER, EnumSet.of(
                VIEW_DASHBOARD, VIEW_JOBS, MANAGE_JOBS, VIEW_CANDIDATES, VIEW_CLIENTS,
                VIEW_INTERVIEWS, VIEW_REPORTS));
        MATRIX.put(Role.HIRING_MANAGER, EnumSet.of(
                VIEW_DASHBOARD, VIEW_JOBS, VIEW_CANDIDATES, VIEW_INTERVIEWS, VIEW_REPORTS));
        MATRIX.put(Role.ACCOUNT_MANAGER, EnumSet.of(
                VIEW_DASHBOARD, VIEW_JOBS, VIEW_CANDIDATES, VIEW_CLIENTS, MANAGE_CLIENTS,
                VIEW_INTERVIEWS, VIEW_REPORTS));
        MATRIX.put(Role.INTERVIEWER, EnumSet.of(VIEW_DASHBOARD, VIEW_INTERVIEWS));
        MATRIX.put(Role.APPROVER, EnumSet.of(VIEW_DASHBOARD, VIEW_APPROVALS));
    }

    private RolePermissions() {}

    public static Set<Capability> capabilitiesFor(Collection<Role> roles) {
        Set<Capability> result = EnumSet.noneOf(Capability.class);
        for (Role role : roles) {
            result.addAll(MATRIX.get(role));
        }
        return result;
    }
}
