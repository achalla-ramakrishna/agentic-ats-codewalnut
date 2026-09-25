package com.codewalnut.ats.config;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.repository.AppUserRepository;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds one obviously-fake user per role (plus a multi-role lead) in the dev and demo profiles only. */
@Component
@Profile({"dev", "demo"})
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final AppUserRepository userRepository;

    private record Seed(String email, String name, EnumSet<Role> roles) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("admin@codewalnut.test", "Dev Admin", EnumSet.of(Role.ADMIN)),
            new Seed("recruiter@codewalnut.test", "Dev Recruiter", EnumSet.of(Role.RECRUITER)),
            new Seed("hiring.manager@codewalnut.test", "Dev Hiring Manager", EnumSet.of(Role.HIRING_MANAGER)),
            new Seed("account.manager@codewalnut.test", "Dev Account Manager", EnumSet.of(Role.ACCOUNT_MANAGER)),
            new Seed("interviewer@codewalnut.test", "Dev Interviewer", EnumSet.of(Role.INTERVIEWER)),
            new Seed("approver@codewalnut.test", "Dev Approver", EnumSet.of(Role.APPROVER)),
            new Seed("lead@codewalnut.test", "Dev Delivery Lead",
                    EnumSet.of(Role.HIRING_MANAGER, Role.INTERVIEWER)));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Seed seed : SEEDS) {
            if (!userRepository.existsByEmail(seed.email())) {
                userRepository.save(AppUser.builder()
                        .email(seed.email())
                        .name(seed.name())
                        .roles(EnumSet.copyOf(seed.roles()))
                        .build());
            }
        }
    }
}
