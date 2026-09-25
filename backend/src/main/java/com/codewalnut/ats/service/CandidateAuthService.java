package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.repository.CandidateAccountRepository;
import com.codewalnut.ats.security.LoginRejectedException;
import com.codewalnut.ats.security.SessionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate sign-in: any verified email outside the staff domains gets a candidate account on
 * first sign-in. Candidates only ever hold the CANDIDATE authority.
 */
@Service
@RequiredArgsConstructor
public class CandidateAuthService {

    private final CandidateAccountRepository accountRepository;
    private final AuditService auditService;

    /** @param email already normalised (trimmed, lower-case) */
    @Transactional
    public CandidateAccount completeLogin(String email, String name, String googleSubject) {
        CandidateAccount account = accountRepository.findByEmail(email).orElse(null);
        if (account == null) {
            account = accountRepository.save(CandidateAccount.builder()
                    .email(email)
                    .name(name)
                    .googleSubject(googleSubject)
                    .build());
            auditService.recordAnonymous(email, AuditAction.CANDIDATE_ACCOUNT_CREATED,
                    Map.of("method", googleSubject != null ? "google" : "dev"));
        } else if (googleSubject != null) {
            if (account.getGoogleSubject() != null && !account.getGoogleSubject().equals(googleSubject)) {
                auditService.recordAnonymous(email, AuditAction.CANDIDATE_LOGIN_REJECTED,
                        Map.of("reason", "Google account does not match the one on record"));
                throw new LoginRejectedException("Google account does not match the one on record");
            }
            account.setGoogleSubject(googleSubject);
        }
        if (account.getName() == null && name != null) {
            account.setName(name);
        }
        account.setLastLoginAt(Instant.now());
        auditService.recordAnonymous(email, AuditAction.CANDIDATE_LOGIN,
                Map.of("method", googleSubject != null ? "google" : "dev"));
        return account;
    }

    public static List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(SessionType.CANDIDATE.authority()));
    }
}
