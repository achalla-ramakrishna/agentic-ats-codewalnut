package com.codewalnut.ats.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.PrivateDocumentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** DOCSTORE-06: cleanup's opt-in cannot bypass the separate operations gate. */
@SpringBootTest(properties = {
        "ats.document-storage.enabled=true", "ats.document-storage.operations-enabled=false",
        "ats.document-storage.cleanup-enabled=true", "ats.document-storage.poll-ms=86400000",
        "ats.document-storage.bridge-url=http://localhost:3001",
        "ats.document-storage.bridge-secret=fake-cleanup-test-secret-at-least-32-characters"
})
@AutoConfigureMockMvc
class DocumentStorageCleanupOperationsDisabledFlowTest {
    @Autowired private MockMvc mvc;
    @MockBean private PrivateDocumentStore store;

    @Test
    void DOCSTORE06_cleanupRequiresOperationsEnablementEvenForAdmins() throws Exception {
        mvc.perform(post("/api/v1/admin/document-storage/cleanup").with(user("admin@codewalnut.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"confirmation":"REMOVE VERIFIED LEGACY DOCUMENT BYTES",
                             "backupReference":"fake-verified-backup","restoreVerified":true,
                             "observedBefore":"2020-01-01T00:00:00Z","dryRun":false}
                            """))
                .andExpect(status().isNotFound());
        verifyNoInteractions(store);
    }
}
