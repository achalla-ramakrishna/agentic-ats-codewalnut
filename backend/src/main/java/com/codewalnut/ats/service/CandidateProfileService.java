package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.dto.ProfileDtos.CandidateProfile;
import com.codewalnut.ats.dto.ProfileDtos.UpdateProfileRequest;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate profile for hiring and background verification: edited by staff, or by the
 * candidate in their own page. The audit log records which fields changed, never the values.
 * See docs/features/candidate-profile-and-bgv.md.
 */
@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public CandidateProfile profile(AppUser actor, UUID candidateId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        return CandidateProfile.from(candidate(candidateId));
    }

    @Transactional
    public CandidateProfile update(AppUser actor, UUID candidateId, UpdateProfileRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        Candidate candidate = candidate(candidateId);
        List<String> changed = apply(candidate, request, true);
        if (!changed.isEmpty()) {
            candidate.setProfileUpdatedAt(Instant.now());
            candidateRepository.save(candidate);
            auditService.record(actor, AuditAction.CANDIDATE_UPDATED, "Candidate", candidateId, Map.of("changed", changed));
        }
        return CandidateProfile.from(candidate);
    }

    @Transactional(readOnly = true)
    public CandidateProfile myProfile(CandidateAccount account) {
        return CandidateProfile.from(mine(account));
    }

    @Transactional
    public CandidateProfile updateMine(CandidateAccount account, UpdateProfileRequest request) {
        Candidate candidate = mine(account);
        List<String> changed = apply(candidate, request, false);
        if (!changed.isEmpty()) {
            candidate.setProfileUpdatedAt(Instant.now());
            candidateRepository.save(candidate);
            auditService.recordAnonymous(account.getEmail(), AuditAction.CANDIDATE_PROFILE_UPDATED,
                    Map.of("candidateId", candidate.getId(), "changed", changed));
            for (Application application : applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId())) {
                eventRepository.save(ApplicationEvent.builder()
                        .application(application)
                        .type(ApplicationEventType.NOTE)
                        .note("Candidate updated their profile: " + String.join(", ", changed))
                        .actorEmail(account.getEmail())
                        .build());
            }
        }
        return CandidateProfile.from(candidate);
    }

    /** The candidate record behind a candidate sign-in; they exist once they've applied or been added. */
    Candidate mine(CandidateAccount account) {
        return candidateRepository.findByEmail(account.getEmail().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("No application yet. Apply using a job link first."));
    }

    private Candidate candidate(UUID id) {
        return candidateRepository.findById(id).orElseThrow(() -> new NotFoundException("Candidate not found"));
    }

    /** Applies the request and returns the names of the fields that changed. */
    private List<String> apply(Candidate c, UpdateProfileRequest r, boolean emailAllowed) {
        List<String> changed = new ArrayList<>();
        if (r.name() != null && !r.name().isBlank()) {
            set(changed, "name", c.getName(), r.name().strip().replaceAll("\\s+", " "), c::setName);
        }
        if (emailAllowed && r.email() != null) {
            String email = blankToNull(r.email()) == null ? null : r.email().strip().toLowerCase(Locale.ROOT);
            if (!Objects.equals(email, c.getEmail()) && email != null
                    && candidateRepository.findByEmail(email).filter(o -> !o.getId().equals(c.getId())).isPresent()) {
                throw new ConflictException("Another candidate already has this email address");
            }
            set(changed, "email", c.getEmail(), email, c::setEmail);
        }
        if (r.phone() != null) {
            String phone = blankToNull(r.phone()) == null ? null : r.phone().replaceAll("[^0-9+]", "");
            if (phone != null && phone.replaceAll("\\D", "").length() < 10) {
                throw new IllegalArgumentException("phone: please enter at least 10 digits");
            }
            set(changed, "phone", c.getPhone(), phone, c::setPhone);
        }
        if (r.dateOfBirth() != null) {
            set(changed, "date of birth", c.getDateOfBirth(), parseDateOfBirth(r.dateOfBirth()), c::setDateOfBirth);
        }
        if (r.currentAddress() != null) {
            set(changed, "current address", c.getCurrentAddress(), blankToNull(r.currentAddress()), c::setCurrentAddress);
        }
        if (r.permanentAddress() != null) {
            set(changed, "permanent address", c.getPermanentAddress(), blankToNull(r.permanentAddress()), c::setPermanentAddress);
        }
        if (r.college() != null) {
            set(changed, "college", c.getCollege(), blankToNull(r.college()), c::setCollege);
        }
        if (r.degree() != null) {
            set(changed, "degree", c.getDegree(), blankToNull(r.degree()), c::setDegree);
        }
        if (r.graduationYear() != null) {
            Integer year = r.graduationYear() == 0 ? null : r.graduationYear();
            if (year != null && year < 1950) {
                throw new IllegalArgumentException("graduationYear: please enter a year like 2025");
            }
            set(changed, "graduation year", c.getGraduationYear(), year, c::setGraduationYear);
        }
        if (r.linkedinUrl() != null) {
            String url = blankToNull(r.linkedinUrl());
            if (url != null && !url.matches("(?i)^https?://[^\\s]+$")) {
                throw new IllegalArgumentException("linkedinUrl: please paste the full link, starting with https://");
            }
            set(changed, "LinkedIn", c.getLinkedinUrl(), url, c::setLinkedinUrl);
        }
        if (r.emergencyContact() != null) {
            set(changed, "emergency contact", c.getEmergencyContact(), blankToNull(r.emergencyContact()), c::setEmergencyContact);
        }
        return changed;
    }

    private static <T> void set(List<String> changed, String field, T current, T next, Consumer<T> setter) {
        if (!Objects.equals(current, next)) {
            setter.accept(next);
            changed.add(field);
        }
    }

    static LocalDate parseDateOfBirth(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.strip());
            LocalDate today = LocalDate.now();
            if (date.isAfter(today.minusYears(14)) || date.isBefore(today.minusYears(100))) {
                throw new IllegalArgumentException("dateOfBirth: please check the date of birth");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dateOfBirth: use the format yyyy-mm-dd");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
