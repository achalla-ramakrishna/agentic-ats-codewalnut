package com.codewalnut.ats.dto;

import com.codewalnut.ats.service.ContactExportService.Format;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Export an opening's candidate contacts; applicationIds empty or missing = everyone in the opening. */
public record ContactExportRequest(@NotNull Format format, List<UUID> applicationIds) {}
