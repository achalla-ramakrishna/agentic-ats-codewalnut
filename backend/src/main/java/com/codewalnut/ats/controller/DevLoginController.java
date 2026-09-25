package com.codewalnut.ats.controller;

import com.codewalnut.ats.dto.DevLoginRequest;
import com.codewalnut.ats.security.LoginRejectedException;
import com.codewalnut.ats.security.UnauthenticatedException;
import com.codewalnut.ats.service.SignInService;
import com.codewalnut.ats.service.SignInService.SignedIn;
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
 * Password-less sign-in for local development without Google credentials: a provisioned staff
 * email signs in as that user; any other email signs in as a candidate (as Google would). Exists only in the dev profile AND when ats.auth.dev-login-enabled=true; in any
 * other environment the bean is absent and the path returns 404.
 */
@RestController
@Profile("dev")
@ConditionalOnProperty(prefix = "ats.auth", name = "dev-login-enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevLoginController {

    private final SignInService signInService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/api/v1/auth/dev-login")
    public ResponseEntity<Void> login(@Valid @RequestBody DevLoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        SignedIn signedIn;
        try {
            signedIn = signInService.signIn(body.email(), null, null);
        } catch (LoginRejectedException ex) {
            throw new UnauthenticatedException(ex.getMessage());
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                signedIn.email(), null, signedIn.authorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }
}
