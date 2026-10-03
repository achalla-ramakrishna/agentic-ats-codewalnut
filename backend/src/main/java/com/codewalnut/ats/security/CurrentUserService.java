package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.repository.AppUserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the signed-in staff AppUser from the session on every request, so role changes and
 * deactivation take effect immediately rather than at the next sign-in. Candidate and client
 * sessions are refused (403) — no staff API is reachable by them. While an admin views as a staff
 * role, the admin is returned with only that role; while viewing as a candidate or client contact,
 * staff APIs are refused like for them.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public AppUser require() {
        var viewAs = ViewAs.current();
        if (viewAs.isPresent()) {
            if (viewAs.get().kind() != ViewAs.Kind.ROLE) {
                throw new AccessDeniedException("Staff only");
            }
            // The admin themselves, with only the previewed role (ADR-0013). Never saved.
            AppUser admin = real();
            return AppUser.builder()
                    .id(admin.getId())
                    .email(admin.getEmail())
                    .name(admin.getName() + " (as " + viewAs.get().label() + ")")
                    .active(true)
                    .roles(new java.util.HashSet<>(java.util.Set.of(viewAs.get().role())))
                    .build();
        }
        return real();
    }

    /** The signed-in staff member, ignoring "view as". */
    public AppUser real() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Not signed in");
        }
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(SessionType.CANDIDATE.authority())
                        || a.getAuthority().equals(SessionType.CLIENT.authority()))) {
            throw new AccessDeniedException("Staff only");
        }
        String email = auth.getName().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthenticatedException("User is not provisioned or inactive"));
    }
}
