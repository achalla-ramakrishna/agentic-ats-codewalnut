package com.codewalnut.ats.dto;

import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.DocumentRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProfileDtos {

    private ProfileDtos() {}

    public record CandidateProfile(
            UUID id, String name, String email, String phone, LocalDate dateOfBirth, String currentAddress,
            String permanentAddress, String college, String degree, Integer graduationYear, String linkedinUrl,
            String emergencyContact, Instant profileUpdatedAt) {

        public static CandidateProfile from(Candidate c) {
            return new CandidateProfile(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getDateOfBirth(),
                    c.getCurrentAddress(), c.getPermanentAddress(), c.getCollege(), c.getDegree(), c.getGraduationYear(),
                    c.getLinkedinUrl(), c.getEmergencyContact(), c.getProfileUpdatedAt());
        }
    }

    /**
     * Any field left out (null) is unchanged; a blank string clears it (the name can't be cleared).
     * dateOfBirth is yyyy-mm-dd; graduationYear 0 clears it. Candidates can't change their email.
     */
    public record UpdateProfileRequest(
            @Size(max = 200) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 10) String dateOfBirth,
            @Size(max = 1000) String currentAddress,
            @Size(max = 1000) String permanentAddress,
            @Size(max = 200) String college,
            @Size(max = 200) String degree,
            @Min(0) @Max(2100) Integer graduationYear,
            @Size(max = 300) String linkedinUrl,
            @Size(max = 300) String emergencyContact) {}

    public record DocumentKindOption(DocumentKind key, String label, boolean sensitive, boolean candidateUploadable) {}

    public record DocumentRequestResponse(
            UUID id, DocumentKind kind, String label, String requestedBy, Instant requestedAt, Instant fulfilledAt) {

        public static DocumentRequestResponse from(DocumentRequest r) {
            return new DocumentRequestResponse(r.getId(), r.getKind(), r.getKind().getLabel(), r.getRequestedBy(),
                    r.getRequestedAt(), r.getFulfilledAt());
        }
    }

    public record RequestDocumentsRequest(@NotEmpty @Size(max = 10) List<DocumentKind> kinds) {}

    /** What a candidate sees about their own documents. */
    public record MyDocument(UUID id, DocumentKind kind, String label, String fileName, Instant uploadedAt) {}

    public record MyDocumentsResponse(List<MyDocument> documents, List<DocumentRequestResponse> requested) {}
}
