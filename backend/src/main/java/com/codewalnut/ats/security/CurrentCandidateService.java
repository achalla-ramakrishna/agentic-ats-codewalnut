package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.repository.CandidateAccountRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the signed-in candidate. Staff sessions are refused (403) on candidate endpoints. */
@Component
@RequiredArgsConstructor
public class CurrentCandidateService {

    private final CandidateAccountRepository accountRepository;

    public CandidateAccount require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Not signed in");
        }
        if (auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals(SessionType.CANDIDATE.authority()))) {
            throw new AccessDeniedException("Candidates only");
        }
        return accountRepository.findByEmail(auth.getName().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UnauthenticatedException("Candidate account not found"));
    }
}
