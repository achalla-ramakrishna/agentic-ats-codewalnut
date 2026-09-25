package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 200) String name,
        @NotEmpty Set<Role> roles) {}
