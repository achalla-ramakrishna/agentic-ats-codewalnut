package com.codewalnut.ats.controller;

import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AdminUpdateDtos.AdminUpdateView;
import com.codewalnut.ats.dto.AdminUpdateDtos.AdminUpdatesPage;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.security.CurrentUserService;
import com.codewalnut.ats.service.AdminUpdateService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminUpdateController {

    private final AdminUpdateService adminUpdateService;
    private final ApplicationRepository applicationRepository;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/v1/admin-updates")
    @Transactional(readOnly = true)
    public AdminUpdatesPage list() {
        var updates = adminUpdateService.latest(currentUserService.require());
        Map<UUID, UUID> jobs = new HashMap<>();
        applicationRepository.findAllById(updates.stream().map(u -> u.getApplicationId()).distinct().toList())
                .forEach(a -> jobs.put(a.getId(), a.getJob().getId()));
        return new AdminUpdatesPage(updates.stream().map(u -> new AdminUpdateView(u.getId(), u.getKind(), u.getApplicationId(),
                        jobs.get(u.getApplicationId()), u.getInterviewId(), u.getTitle(), u.getBody(), u.getActorEmail(),
                        u.getEmailStatus(), u.getEmailedTo(), u.getCreatedAt())).toList(),
                adminUpdateService.keyStages().stream().sorted().map(Stage::getLabel).toList());
    }
}
