package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codewalnut.ats.config.OperationsMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Uses the deployment's short environment names to exercise application.yml binding and filter ordering. */
@SpringBootTest(properties = {"spring.profiles.active=default", "ATS_MAINTENANCE_ENABLED=true",
        "ATS_REHEARSAL_ENABLED=false", "ATS_BACKGROUND_WORK_ENABLED=true"})
@AutoConfigureMockMvc
class MaintenanceFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private OperationsMode operations;

    @Test
    void DEPLOY_01_realFilterBlocksAnonymousReadsWritesAndCsrfExemptWebhooks() throws Exception {
        assertThat(operations.maintenance()).isTrue();
        assertThat(operations.backgroundWorkEnabled()).isFalse();
        mvc.perform(post("/webhooks/whatsapp").content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.error").value("Scheduled maintenance. Please try again shortly."));
        mvc.perform(get("/api/v1/auth/session")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v1/users").content("{}"))
                .andExpect(status().isServiceUnavailable()); // no session or CSRF, so must precede security
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }
}
