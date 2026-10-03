package com.codewalnut.ats.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * While an admin is viewing as someone, nothing can be changed, sent or applied in that person's
 * name: every write to the API is refused, except leaving "view as" and signing out.
 */
@Component
public class ViewAsReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> ALLOWED = Set.of("/api/v1/admin/view-as/stop", "/api/v1/auth/logout");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!SAFE.contains(request.getMethod()) && path.startsWith("/api/") && !ALLOWED.contains(path)) {
            var state = ViewAs.current(request.getSession(false));
            if (state.isPresent()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"You're viewing as " + state.get().label().replace("\"", "'")
                        + " (read-only), so nothing was changed. Click Back to admin to make changes.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
