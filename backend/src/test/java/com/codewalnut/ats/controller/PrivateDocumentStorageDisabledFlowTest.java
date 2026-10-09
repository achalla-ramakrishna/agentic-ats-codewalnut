package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.config.DocumentStorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/** DOCSTORE-05: adding storage support never enables migration on an existing deployment. */
@SpringBootTest
@AutoConfigureMockMvc
class PrivateDocumentStorageDisabledFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private DocumentStorageProperties properties;
    @MockBean private PrivateDocumentStore store;

    @Test
    void DOCSTORE05_defaultConfigurationKeepsOperatorEndpointsUnavailableEvenToAdmins() throws Exception {
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.operationsEnabled()).isFalse();
        String operations = "/api/v1/admin/document-storage";
        mockMvc.perform(get(operations).with(user("admin@codewalnut.test"))).andExpect(status().isNotFound());
        mockMvc.perform(post(operations + "/migrate").with(user("admin@codewalnut.test")).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(operations + "/retry").with(user("admin@codewalnut.test")).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(operations).with(user("recruiter@codewalnut.test"))).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
}
