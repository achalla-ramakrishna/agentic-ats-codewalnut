package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.dto.AuthConfigResponse;
import com.codewalnut.ats.dto.AuthConfigResponse.DevUser;
import com.codewalnut.ats.repository.AppUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthConfigController {

    static final String DEV_CANDIDATE_EMAIL = "dev.candidate@gmail.com";

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final ObjectProvider<DevLoginController> devLogin;
    private final AppUserRepository userRepository;

    @GetMapping("/api/v1/auth/config")
    public AuthConfigResponse config() {
        boolean devLoginEnabled = devLogin.getIfAvailable() != null;
        List<DevUser> devUsers = devLoginEnabled
                ? userRepository.findAllByOrderByEmailAsc().stream()
                        .filter(AppUser::isActive)
                        .map(u -> new DevUser(u.getEmail(), u.rolesView().stream()
                                .map(Role::getLabel).collect(Collectors.joining(", "))))
                        .collect(Collectors.toCollection(ArrayList::new))
                : new ArrayList<DevUser>();
        if (devLoginEnabled) {
            // Any non-staff email signs in as a candidate; offer one for convenience.
            devUsers.add(new DevUser(DEV_CANDIDATE_EMAIL, "Candidate (personal Gmail)"));
        }
        return new AuthConfigResponse(clientRegistrations.getIfAvailable() != null, devLoginEnabled, devUsers);
    }
}
