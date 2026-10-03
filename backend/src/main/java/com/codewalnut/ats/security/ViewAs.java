package com.codewalnut.ats.security;

import com.codewalnut.ats.domain.Role;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * "View as" (ADR-0013): an admin sees the app as a candidate, a client contact or a staff role,
 * read-only. The admin stays signed in as themselves; this session attribute only changes whose
 * screens and data the Current*Service resolvers return. Expires after {@link #DURATION_MINUTES}.
 */
public final class ViewAs {

    public enum Kind { CANDIDATE, CLIENT, ROLE }

    /** email: the candidate's or client contact's; role: for a staff role. label: shown in the banner. */
    public record State(Kind kind, String email, Role role, String label, String adminEmail, Instant expiresAt)
            implements Serializable {}

    public static final String ATTRIBUTE = "ats.viewAs";
    public static final int DURATION_MINUTES = 30;

    private ViewAs() {}

    /** The active "view as" for this request's session, if any (expired ones are cleared). */
    public static Optional<State> current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return Optional.empty();
        }
        HttpSession session = attrs.getRequest().getSession(false);
        return current(session);
    }

    public static Optional<State> current(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value;
        try {
            value = session.getAttribute(ATTRIBUTE);
        } catch (IllegalStateException invalidated) {
            return Optional.empty();
        }
        if (!(value instanceof State state)) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(state.expiresAt())) {
            session.removeAttribute(ATTRIBUTE);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    public static boolean active() {
        return current().isPresent();
    }
}
