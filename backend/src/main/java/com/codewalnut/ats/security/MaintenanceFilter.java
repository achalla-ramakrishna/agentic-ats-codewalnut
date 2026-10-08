package com.codewalnut.ats.security;

import com.codewalnut.ats.config.OperationsMode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** GETs can expire assessments or record views, so a freeze blocks reads as well as writes. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {
    private final OperationsMode operations;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (operations.maintenance() && !("GET".equals(request.getMethod())
                && "/api/v1/health".equals(request.getRequestURI()))) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "300");
            response.setHeader("Cache-Control", "private, no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Scheduled maintenance. Please try again shortly.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
