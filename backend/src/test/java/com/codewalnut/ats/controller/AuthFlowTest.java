package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockHttpSession devLogin(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void apiRequiresSignInAndAnswers401NotARedirect() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void authConfigAdvertisesDevLoginInDevProfile() throws Exception {
        mockMvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEnabled").value(false))
                .andExpect(jsonPath("$.devLoginEnabled").value(true))
                .andExpect(jsonPath("$.devUsers[*].email").value(hasItem("admin@codewalnut.test")));
    }

    @Test
    void devLoginStartsASessionWithRoleBasedNavigation() throws Exception {
        MockHttpSession session = devLogin("lead@codewalnut.test");

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lead@codewalnut.test"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.navigation[*].key").value(hasItem("interviews")))
                .andExpect(jsonPath("$.navigation[*].key").value(hasItem("candidates")));

        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.LOGIN, "lead@codewalnut.test")).isNotEmpty();
    }

    @Test
    void devLoginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@codewalnut.test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownUserCannotSignInAndTheAttemptIsAudited() throws Exception {
        mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stranger@codewalnut.test\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                AuditAction.LOGIN_REJECTED, "stranger@codewalnut.test")).isNotEmpty();
    }

    @Test
    void emailOutsideAllowedDomainsCannotSignIn() throws Exception {
        mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = devLogin("recruiter@codewalnut.test");

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
    }
}
