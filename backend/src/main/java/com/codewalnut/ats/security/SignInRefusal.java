package com.codewalnut.ats.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Why the last Google sign-in in this browser session was refused, so the login page can say
 * exactly what to fix (e.g. "arun.k@codewalnut.com isn't a user yet"). Only the person who
 * just signed in with that Google account sees it, and it is shown once.
 */
public record SignInRefusal(String email, String reason) {

    public static final String NOT_PROVISIONED = "NOT_PROVISIONED";
    public static final String DEACTIVATED = "DEACTIVATED";
    public static final String ACCOUNT_MISMATCH = "ACCOUNT_MISMATCH";
    public static final String UNVERIFIED = "UNVERIFIED";
    public static final String OTHER = "OTHER";

    private static final String ATTRIBUTE = SignInRefusal.class.getName();

    /** Maps a rejection message from the sign-in services to a reason code. */
    public static String reasonFor(String message) {
        if (message == null) return OTHER;
        if (message.contains("not provisioned")) return NOT_PROVISIONED;
        if (message.contains("deactivated")) return DEACTIVATED;
        if (message.contains("does not match")) return ACCOUNT_MISMATCH;
        if (message.contains("not verified")) return UNVERIFIED;
        return OTHER;
    }

    static void remember(String email, String reason) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            attributes.getRequest().getSession().setAttribute(ATTRIBUTE, new SignInRefusal(email, reason));
        }
    }

    /** Returns and forgets the refusal, if any. */
    public static SignInRefusal take(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof SignInRefusal refusal)) {
            return null;
        }
        session.removeAttribute(ATTRIBUTE);
        return refusal;
    }
}
