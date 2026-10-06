package com.codewalnut.ats.service;

import com.codewalnut.ats.dto.NavItem;
import com.codewalnut.ats.security.Capability;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The app's navigation, filtered by what the user may see. The server decides; the SPA only
 * renders what it is given.
 */
@Service
public class NavigationService {

    private record Entry(String key, String label, String path, Capability capability) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("dashboard", "Dashboard", "/", Capability.VIEW_DASHBOARD),
            new Entry("ask", "Ask ATS", "/ask", Capability.VIEW_DASHBOARD),
            new Entry("jobs", "Openings", "/jobs", Capability.VIEW_JOBS),
            new Entry("candidates", "Candidates", "/candidates", Capability.VIEW_CANDIDATES),
            new Entry("workflow", "Workflow", "/workflow", Capability.VIEW_CANDIDATES),
            new Entry("messages", "Messages", "/messages", Capability.MESSAGE_CANDIDATES),
            new Entry("clients", "Clients", "/clients", Capability.VIEW_CLIENTS),
            new Entry("interviews", "Interviews", "/interviews", Capability.VIEW_INTERVIEWS),
            new Entry("interview-questions", "Interview questions", "/interview-questions", Capability.VIEW_INTERVIEWS),
            new Entry("tests", "Tests", "/tests", Capability.MANAGE_JOBS),
            new Entry("approvals", "Approvals", "/approvals", Capability.VIEW_APPROVALS),
            new Entry("reports", "Reports", "/reports", Capability.VIEW_REPORTS),
            new Entry("admin-updates", "Admin updates", "/admin/updates", Capability.VIEW_ADMIN_UPDATES),
            new Entry("users", "Users", "/admin/users", Capability.MANAGE_USERS),
            new Entry("view-as", "View as", "/admin/view-as", Capability.MANAGE_USERS),
            new Entry("audit-log", "Audit log", "/admin/audit-log", Capability.VIEW_AUDIT_LOG));

    public List<NavItem> navigationFor(Set<Capability> capabilities) {
        return ENTRIES.stream()
                .filter(entry -> capabilities.contains(entry.capability()))
                .map(entry -> new NavItem(entry.key(), entry.label(), entry.path()))
                .toList();
    }
}
