package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Role;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Partial update: null fields are left unchanged. An empty role set is rejected. */
public record UpdateUserRequest(
        @Size(max = 200) String name,
        @Size(min = 1, message = "must contain at least one role") Set<Role> roles,
        Boolean active) {}
