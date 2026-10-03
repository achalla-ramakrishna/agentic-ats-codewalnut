package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.security.ViewAs;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** "View as" for admins (ADR-0013). */
public final class ViewAsDtos {

    private ViewAsDtos() {}

    public record CandidateOption(String email, String name, List<String> openings) {}

    public record ClientOption(String email, String name, String clientName) {}

    public record RoleOption(Role role, String label) {}

    public record Options(List<CandidateOption> candidates, List<ClientOption> clients, List<RoleOption> roles,
            int minutes) {}

    public record StartRequest(@NotNull ViewAs.Kind kind, String email, Role role) {}

    /** What the banner shows. */
    public record Info(ViewAs.Kind kind, String label, Instant expiresAt) {}
}
