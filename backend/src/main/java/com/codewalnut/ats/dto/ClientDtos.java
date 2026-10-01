package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.ClientShare;
import com.codewalnut.ats.domain.DocumentKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ClientDtos {

    private ClientDtos() {}

    // ---- staff ----

    public record ClientContactResponse(
            UUID id, UUID clientId, String email, String name, boolean active, Instant lastLoginAt) {

        public static ClientContactResponse from(ClientContact c) {
            return new ClientContactResponse(c.getId(), c.getClient().getId(), c.getEmail(), c.getName(), c.isActive(),
                    c.getLastLoginAt());
        }
    }

    public record AddContactRequest(@NotBlank @Email @Size(max = 254) String email, @Size(max = 200) String name) {}

    public record ShareRequest(
            boolean includeContact,
            boolean includeProfile,
            @NotNull @Size(max = 20) List<UUID> documentIds,
            @Size(max = 1000) String note) {}

    /** active=false with sharedAt null: never shared. */
    public record ShareResponse(
            UUID applicationId, String clientName, boolean active, boolean includeContact, boolean includeProfile,
            Set<UUID> documentIds, String note, String sharedBy, Instant sharedAt, Instant revokedAt, Instant lastViewedAt) {

        public static ShareResponse none(UUID applicationId, String clientName) {
            return new ShareResponse(applicationId, clientName, false, false, false, Set.of(), null, null, null, null, null);
        }

        public static ShareResponse from(ClientShare s) {
            return new ShareResponse(s.getApplication().getId(), s.getClient().getName(), s.isActive(),
                    s.isIncludeContact(), s.isIncludeProfile(), Set.copyOf(s.getDocumentIds()), s.getNote(),
                    s.getSharedBy(), s.getSharedAt(), s.getRevokedAt(), s.getLastViewedAt());
        }
    }

    // ---- client portal ----

    public record ClientMe(String email, String name, String clientName) {}

    public record SharedDocument(UUID id, DocumentKind kind, String label, String fileName, Instant uploadedAt) {}

    /** Profile details without contact details (those are shared separately). */
    public record SharedProfile(
            LocalDate dateOfBirth, String currentAddress, String permanentAddress, String college, String degree,
            Integer graduationYear, String linkedinUrl, String emergencyContact) {

        public static SharedProfile from(Candidate c) {
            return new SharedProfile(c.getDateOfBirth(), c.getCurrentAddress(), c.getPermanentAddress(), c.getCollege(),
                    c.getDegree(), c.getGraduationYear(), c.getLinkedinUrl(), c.getEmergencyContact());
        }
    }

    /** email, phone and profile are null unless CodeWalnut shared them. */
    public record ClientCandidate(
            UUID applicationId, String name, String jobTitle, String stageLabel, Instant sharedAt, String note,
            String email, String phone, SharedProfile profile, List<SharedDocument> documents) {}
}
