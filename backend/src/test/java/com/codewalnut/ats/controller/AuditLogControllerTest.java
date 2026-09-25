package com.codewalnut.ats.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminReadsTheAuditLogNewestFirstWithACappedPageSize() throws Exception {
        // Generate at least one entry.
        mockMvc.perform(get("/api/v1/users").with(user("interviewer@codewalnut.test")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit-log?size=1000").with(user("admin@codewalnut.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.items[0].action").exists());
    }

    @Test
    void nonAdminCannotReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/api/v1/audit-log").with(user("approver@codewalnut.test")))
                .andExpect(status().isForbidden());
    }
}
