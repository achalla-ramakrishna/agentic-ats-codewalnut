package com.codewalnut.ats.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.Role;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RolePermissionsTest {

    @Test
    void adminHasEveryCapability() {
        assertThat(RolePermissions.capabilitiesFor(List.of(Role.ADMIN)))
                .containsExactlyInAnyOrder(Capability.values());
    }

    @Test
    void interviewerSeesOnlyDashboardAndInterviews() {
        assertThat(RolePermissions.capabilitiesFor(List.of(Role.INTERVIEWER)))
                .containsExactlyInAnyOrder(Capability.VIEW_DASHBOARD, Capability.VIEW_INTERVIEWS);
    }

    @Test
    void onlyAdminCanManageUsersOrReadTheAuditLog() {
        for (Role role : EnumSet.complementOf(EnumSet.of(Role.ADMIN))) {
            assertThat(RolePermissions.capabilitiesFor(List.of(role)))
                    .as(role.name())
                    .doesNotContain(Capability.MANAGE_USERS, Capability.VIEW_AUDIT_LOG);
        }
    }

    @Test
    void onlyAdminAndAccountManagerCanManageClients() {
        for (Role role : Role.values()) {
            boolean expected = role == Role.ADMIN || role == Role.ACCOUNT_MANAGER;
            assertThat(RolePermissions.capabilitiesFor(List.of(role)).contains(Capability.MANAGE_CLIENTS))
                    .as(role.name())
                    .isEqualTo(expected);
        }
    }

    @Test
    void multipleRolesGetTheUnion() {
        assertThat(RolePermissions.capabilitiesFor(List.of(Role.HIRING_MANAGER, Role.APPROVER)))
                .contains(Capability.VIEW_CANDIDATES, Capability.VIEW_APPROVALS);
    }

    @Test
    void noRolesMeansNoCapabilities() {
        assertThat(RolePermissions.capabilitiesFor(List.of())).isEmpty();
    }
}
