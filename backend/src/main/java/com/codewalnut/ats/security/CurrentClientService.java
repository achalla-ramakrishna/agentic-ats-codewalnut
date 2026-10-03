package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.repository.ClientContactRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the signed-in client contact on every request, so removing a contact cuts access
 * immediately. Staff and candidate sessions are refused (403) on client endpoints.
 */
@Component
@RequiredArgsConstructor
public class CurrentClientService {

    private final ClientContactRepository contactRepository;

    public ClientContact require() {
        var viewAs = ViewAs.current();
        if (viewAs.isPresent()) {
            if (viewAs.get().kind() != ViewAs.Kind.CLIENT) {
                throw new AccessDeniedException("Client contacts only");
            }
            return contactRepository.findByEmail(viewAs.get().email())
                    .filter(ClientContact::isActive)
                    .orElseThrow(() -> new UnauthenticatedException("This client contact has been removed"));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Not signed in");
        }
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(SessionType.CLIENT.authority()))) {
            throw new AccessDeniedException("Client contacts only");
        }
        return contactRepository.findByEmail(auth.getName().toLowerCase(Locale.ROOT))
                .filter(ClientContact::isActive)
                .orElseThrow(() -> new UnauthenticatedException("Your access has been removed"));
    }
}
