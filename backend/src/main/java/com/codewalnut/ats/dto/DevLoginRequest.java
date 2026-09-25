package com.codewalnut.ats.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** accessCode is required only in the demo profile. */
public record DevLoginRequest(@NotBlank @Email String email, String accessCode) {}
