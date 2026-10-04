package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.AdminUpdate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin updates (ADM-14…): admins only. */
public final class AdminUpdateDtos {

    private AdminUpdateDtos() {}

    public record AdminUpdateView(
            UUID id, AdminUpdate.Kind kind, UUID applicationId, UUID jobId, UUID interviewId, String title, String body,
            String actorEmail, AdminUpdate.EmailStatus emailStatus, String emailedTo, Instant createdAt) {}

    /** keyStages: the stages that send an update, as labels. */
    public record AdminUpdatesPage(List<AdminUpdateView> updates, List<String> keyStages) {}
}
