package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;

/** AUTH-21: the demo profile's dev login requires the shared access code. */
@SpringBootTest(properties = {"spring.profiles.active=demo", "ats.auth.demo-access-code=preview-code-1234"})
@AutoConfigureMockMvc
class DemoProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private org.springframework.test.web.servlet.ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void loginPageIsToldTheCodeIsRequired() throws Exception {
        mockMvc.perform(get("/api/v1/auth/config"))
                .andExpect(jsonPath("$.devLoginEnabled").value(true))
                .andExpect(jsonPath("$.accessCodeRequired").value(true));
    }

    @Test
    void missingOrWrongCodeIsRefusedAndAudited() throws Exception {
        login("{\"email\":\"approver@codewalnut.test\"}").andExpect(status().isUnauthorized());
        login("{\"email\":\"approver@codewalnut.test\",\"accessCode\":\"guess\"}").andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findByActionAndActorEmailOrderByCreatedAtDesc(
                        AuditAction.LOGIN_REJECTED, "approver@codewalnut.test"))
                .anyMatch(e -> e.getDetails().contains("demo access code"));
    }

    @Test
    void correctCodeSignsIn() throws Exception {
        login("{\"email\":\"admin@codewalnut.test\",\"accessCode\":\"preview-code-1234\"}")
                .andExpect(status().isNoContent());
    }
}
