package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.dto.NavItem;
import com.codewalnut.ats.security.RolePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavigationServiceTest {

    private final NavigationService navigationService = new NavigationService();

    private List<String> keysFor(Role... roles) {
        return navigationService.navigationFor(RolePermissions.capabilitiesFor(List.of(roles))).stream()
                .map(NavItem::key)
                .toList();
    }

    @Test
    void interviewerNavigation() {
        assertThat(keysFor(Role.INTERVIEWER)).containsExactly("dashboard", "interviews");
    }

    @Test
    void approverNavigation() {
        assertThat(keysFor(Role.APPROVER)).containsExactly("dashboard", "approvals");
    }

    @Test
    void accountManagerSeesClients() {
        assertThat(keysFor(Role.ACCOUNT_MANAGER)).contains("clients").doesNotContain("users", "audit-log");
    }

    @Test
    void adminSeesEverythingInOrder() {
        assertThat(keysFor(Role.ADMIN)).containsExactly("dashboard", "jobs", "candidates", "clients",
                "interviews", "approvals", "reports", "users", "audit-log");
    }
}
