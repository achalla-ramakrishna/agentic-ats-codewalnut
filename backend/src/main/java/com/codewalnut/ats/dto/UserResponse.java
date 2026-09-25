package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id, String email, String name, Set<Role> roles, boolean active, Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.rolesView(),
                user.isActive(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
