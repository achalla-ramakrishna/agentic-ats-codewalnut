package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Role;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.security.LoginRejectedException;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "ats.auth.bootstrap-admins=Founder@CodeWalnut.com")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository userRepository;

    /** Tests run against a real MySQL that outlives the run, so emails must not repeat. */
    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@codewalnut.com";
    }

    @Test
    void bootstrapAdminIsProvisionedOnFirstGoogleSignIn() {
        AppUser user = authService.completeLogin("FOUNDER@codewalnut.com", "Founder", "google-sub-1");

        assertThat(user.getEmail()).isEqualTo("founder@codewalnut.com");
        assertThat(user.getRoles()).containsExactly(Role.ADMIN);
        assertThat(userRepository.findByEmail("founder@codewalnut.com").orElseThrow().getGoogleSubject())
                .isEqualTo("google-sub-1");
    }

    @Test
    void aDifferentGoogleAccountForTheSameEmailIsRejected() {
        String pinned = unique("pinned");
        userRepository.save(AppUser.builder()
                .email(pinned)
                .googleSubject("original-sub")
                .roles(EnumSet.of(Role.RECRUITER))
                .build());

        assertThatThrownBy(() -> authService.completeLogin(pinned, null, "other-sub"))
                .isInstanceOf(LoginRejectedException.class);
    }

    @Test
    void deactivatedUserIsRejected() {
        String former = unique("former");
        userRepository.save(AppUser.builder()
                .email(former)
                .active(false)
                .roles(EnumSet.of(Role.RECRUITER))
                .build());

        assertThatThrownBy(() -> authService.completeLogin(former, null, "sub"))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("deactivated");
    }
}
