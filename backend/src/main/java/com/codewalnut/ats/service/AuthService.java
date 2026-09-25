package com.codewalnut.ats.service;

import com.codewalnut.ats.config.AuthProperties;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.security.LoginRejectedException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a person who has proven their email (via Google, or the dev login) may
 * start a session. Only provisioned, active users in an allowed domain get in; configured
 * bootstrap admins are provisioned on first sign-in.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    @Transactional
    public AppUser completeLogin(String rawEmail, String name, String googleSubject) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!authProperties.isAllowedEmail(email)) {
            throw reject(email, "Email domain is not allowed");
        }
        AppUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null && authProperties.isBootstrapAdmin(email)) {
            user = userRepository.save(AppUser.builder()
                    .email(email)
                    .name(name)
                    .roles(EnumSet.of(Role.ADMIN))
                    .build());
            auditService.record(user, AuditAction.USER_CREATED, "AppUser", user.getId(),
                    Map.of("reason", "bootstrap admin", "roles", List.of(Role.ADMIN)));
        }
        if (user == null) {
            throw reject(email, "User is not provisioned");
        }
        if (!user.isActive()) {
            throw reject(email, "User is deactivated");
        }
        if (user.getName() == null && name != null) {
            user.setName(name);
        }
        if (googleSubject != null) {
            if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(googleSubject)) {
                throw reject(email, "Google account does not match the one on record");
            }
            user.setGoogleSubject(googleSubject);
        }
        user.setLastLoginAt(Instant.now());
        auditService.record(user, AuditAction.LOGIN, "AppUser", user.getId(),
                Map.of("method", googleSubject != null ? "google" : "dev"));
        return user;
    }

    public static List<GrantedAuthority> authoritiesFor(AppUser user) {
        return user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    private LoginRejectedException reject(String email, String reason) {
        auditService.recordAnonymous(email, AuditAction.LOGIN_REJECTED, Map.of("reason", reason));
        return new LoginRejectedException(reason);
    }
}
