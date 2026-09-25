package com.codewalnut.ats.service;

import com.codewalnut.ats.config.AuthProperties;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.dto.CreateUserRequest;
import com.codewalnut.ats.dto.UpdateUserRequest;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    @Transactional(readOnly = true)
    public List<AppUser> list(AppUser actor) {
        accessPolicy.require(actor, Capability.MANAGE_USERS);
        return userRepository.findAllByOrderByEmailAsc();
    }

    @Transactional
    public AppUser create(AppUser actor, CreateUserRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_USERS);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!authProperties.isAllowedEmail(email)) {
            throw new IllegalArgumentException("email: domain is not allowed");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        AppUser user = userRepository.save(AppUser.builder()
                .email(email)
                .name(request.name())
                .roles(EnumSet.copyOf(request.roles()))
                .build());
        auditService.record(actor, AuditAction.USER_CREATED, "AppUser", user.getId(),
                Map.of("email", email, "roles", user.getRoles()));
        return user;
    }

    @Transactional
    public AppUser update(AppUser actor, UUID id, UpdateUserRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_USERS);
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        boolean self = user.getId().equals(actor.getId());
        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.roles() != null) {
            Set<Role> roles = EnumSet.copyOf(request.roles());
            if (self && !roles.contains(Role.ADMIN)) {
                throw new IllegalArgumentException("roles: you cannot remove your own Admin role");
            }
            changes.put("roles", Map.of("from", user.rolesView(), "to", roles));
            user.getRoles().clear();
            user.getRoles().addAll(roles);
        }
        if (request.active() != null && request.active() != user.isActive()) {
            if (self && !request.active()) {
                throw new IllegalArgumentException("active: you cannot deactivate yourself");
            }
            changes.put("active", Map.of("from", user.isActive(), "to", request.active()));
            user.setActive(request.active());
        }
        if (request.name() != null && !request.name().equals(user.getName())) {
            changes.put("name", "changed");
            user.setName(request.name());
        }
        if (!changes.isEmpty()) {
            auditService.record(actor, AuditAction.USER_UPDATED, "AppUser", user.getId(), changes);
        }
        return user;
    }
}
