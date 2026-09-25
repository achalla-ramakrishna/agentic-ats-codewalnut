package com.codewalnut.ats.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DevLoginRequest(@NotBlank @Email String email) {}
