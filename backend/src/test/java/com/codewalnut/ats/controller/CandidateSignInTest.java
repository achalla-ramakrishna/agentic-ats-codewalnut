package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.repository.AuditLogRepository;
import com.codewalnut.ats.repository.CandidateAccountRepository;
import com.codewalnut.ats.security.LoginRejectedException;
import com.codewalnut.ats.security.SessionType;
import com.codewalnut.ats.service.SignInService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** AUTH-18: candidates sign in with any verified Google account (e.g. personal Gmail). */
@SpringBootTest
@AutoConfigureMockMvc
class CandidateSignInTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignInService signInService;

    @Autowired
    private CandidateAccountRepository candidateAccountRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static String gmail() {
        return "cand." + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
    }

    private MockHttpSession devLogin(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void personalGmailSignsInAsACandidateAndGetsAnAccountOnce() {
        String email = gmail();

        var first = signInService.signIn(email.toUpperCase(), "Asha Rao", "google-sub-a");
        var second = signInService.signIn(email, null, "google-sub-a");

        assertThat(first.type()).isEqualTo(SessionType.CANDIDATE);
        assertThat(second.type()).isEqualTo(SessionType.CANDIDATE);
        assertThat(candidateAccountRepository.findByEmail(email)).get()
                .satisfies(a -> assertThat(a.getName()).isEqualTo("Asha Rao"));
        assertThat(appUserRepository.findByEmail(email)).isEmpty();
        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.CANDIDATE_ACCOUNT_CREATED, email)).hasSize(1);
        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.CANDIDATE_LOGIN, email)).hasSize(2);
    }

    @Test
    void aDifferentGoogleAccountForTheSameCandidateEmailIsRejected() {
        String email = gmail();
        signInService.signIn(email, null, "google-sub-1");

        assertThatThrownBy(() -> signInService.signIn(email, null, "google-sub-2"))
                .isInstanceOf(LoginRejectedException.class);
    }

    @Test
    void provisionedStaffStillSignInAsStaff() {
        assertThat(signInService.signIn("recruiter@codewalnut.test", null, null).type())
                .isEqualTo(SessionType.STAFF);
    }

    @Test
    void candidateSessionSeesTheCandidateAreaButNoStaffApi() throws Exception {
        String email = gmail();
        MockHttpSession session = devLogin(email);

        mockMvc.perform(get("/api/v1/auth/session").session(session))
                .andExpect(jsonPath("$.type").value("CANDIDATE"));
        mockMvc.perform(get("/api/v1/candidate/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit-log").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void staffSessionCannotUseCandidateEndpoints() throws Exception {
        MockHttpSession session = devLogin("admin@codewalnut.test");

        mockMvc.perform(get("/api/v1/auth/session").session(session))
                .andExpect(jsonPath("$.type").value("STAFF"));
        mockMvc.perform(get("/api/v1/candidate/me").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void signedOutSessionHasNoType() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").doesNotExist());
        mockMvc.perform(get("/api/v1/candidate/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void devLoginOffersACandidateOption() throws Exception {
        mockMvc.perform(get("/api/v1/auth/config"))
                .andExpect(jsonPath("$.devUsers[?(@.email == 'dev.candidate@gmail.com')]").exists());
    }
}
