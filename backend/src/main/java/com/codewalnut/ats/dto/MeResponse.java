package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.security.Capability;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MeResponse(
        UUID id, String email, String name, Set<Role> roles, Set<Capability> capabilities,
        List<NavItem> navigation) {}
