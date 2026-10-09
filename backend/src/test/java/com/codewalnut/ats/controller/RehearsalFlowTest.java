package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codewalnut.ats.config.OperationsMode;
import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import com.codewalnut.ats.task.DocumentStorageWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.profiles.active=default", "ATS_REHEARSAL_ENABLED=true",
        "ATS_MAINTENANCE_ENABLED=false", "ATS_BACKGROUND_WORK_ENABLED=true", "ATS_DOCUMENT_STORAGE_ENABLED=true"})
@AutoConfigureMockMvc
class RehearsalFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private OperationsMode operations;
    @Autowired private DocumentStorageWorker worker;
    @MockBean private DocumentStorageRepository storage;
    @MockBean private PrivateDocumentStore provider;

    @Test
    void DEPLOY_02_rehearsalOverridesEnvironmentFlagsAndPreventsStoragePolling() throws Exception {
        assertThat(operations.maintenance()).isTrue();
        assertThat(operations.backgroundWorkEnabled()).isFalse();
        mvc.perform(get("/api/v1/auth/session")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/webhooks/whatsapp")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        clearInvocations(storage, provider);
        worker.poll();
        verifyNoInteractions(storage, provider);
    }
}
