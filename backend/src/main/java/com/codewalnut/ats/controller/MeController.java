package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.dto.MeResponse;
import com.codewalnut.ats.security.Capability;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.security.RolePermissions;
import com.codewalnut.ats.service.NavigationService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final CurrentUserService currentUserService;
    private final NavigationService navigationService;

    @GetMapping("/api/v1/me")
    public MeResponse me() {
        AppUser user = currentUserService.require();
        Set<Capability> capabilities = RolePermissions.capabilitiesFor(user.getRoles());
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.rolesView(),
                capabilities, navigationService.navigationFor(capabilities));
    }
}
