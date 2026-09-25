package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.dto.DevLoginRequest;
import com.codewalnut.ats.security.LoginRejectedException;
import com.codewalnut.ats.security.UnauthenticatedException;
import com.codewalnut.ats.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password-less sign-in as an existing user, for local development without Google
 * credentials. Exists only in the dev profile AND when ats.auth.dev-login-enabled=true; in any
 * other environment the bean is absent and the path returns 404.
 */
@RestController
@Profile("dev")
@ConditionalOnProperty(prefix = "ats.auth", name = "dev-login-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevLoginController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/api/v1/auth/dev-login")
    public ResponseEntity<Void> login(@Valid @RequestBody DevLoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        AppUser user;
        try {
            user = authService.completeLogin(body.email(), null, null);
        } catch (LoginRejectedException ex) {
            throw new UnauthenticatedException(ex.getMessage());
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, AuthService.authoritiesFor(user)));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }
}
