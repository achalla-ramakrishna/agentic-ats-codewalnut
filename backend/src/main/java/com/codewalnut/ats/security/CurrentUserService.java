package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.repository.AppUserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the signed-in AppUser from the session on every request, so role changes and
 * deactivation take effect immediately rather than at the next sign-in.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Not signed in");
        }
        String email = auth.getName().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(email)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthenticatedException("User is not provisioned or inactive"));
    }
}
