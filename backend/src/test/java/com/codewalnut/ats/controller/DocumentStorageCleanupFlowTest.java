package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import com.codewalnut.ats.repository.AuditLogRepository;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import com.codewalnut.ats.service.AuditService;
import com.codewalnut.ats.service.DocumentStorageCleanupService;
import com.codewalnut.ats.service.DocumentStorageUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** DOCSTORE-06: real MySQL locks/transactions and real HTTP permissions; no live storage. */
@SpringBootTest(properties = {
        "ats.document-storage.enabled=true", "ats.document-storage.operations-enabled=true",
        "ats.document-storage.cleanup-enabled=true", "ats.document-storage.poll-ms=86400000",
        "ats.document-storage.bridge-url=http://localhost:3001",
        "ats.document-storage.bridge-secret=fake-cleanup-test-secret-at-least-32-characters"
})
@AutoConfigureMockMvc
class DocumentStorageCleanupFlowTest {
    private static final String URL = "/api/v1/admin/document-storage/cleanup";
    private static final Instant NOW = Instant.parse("2025-02-01T00:00:00Z");
    private static final Instant OLD = Instant.parse("2025-01-01T00:00:00Z");
    private static final byte[] PDF = "%PDF-1.4\nObviously fake cleanup fixture".getBytes(StandardCharsets.US_ASCII);
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentStorageRepository tasks;
    @Autowired private AuditLogRepository audits;
    @Autowired private ObjectMapper json;
    @MockBean private PrivateDocumentStore store;
    @MockBean(name = "documentStorageCleanupClock") private Clock clock;
    @SpyBean private AuditService auditService;
    private UUID document;
    private String application;
    private String contact;
    private String key;
    private final Map<String, byte[]> objects = new HashMap<>();

    @BeforeEach
    void fixture() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        String tag = UUID.randomUUID().toString();
        String client = id(postJson("/api/v1/clients", "{\"name\":\"Fake cleanup client " + tag + "\"}"));
        contact = "cleanup." + tag + "@client.example";
        postJson("/api/v1/clients/" + client + "/contacts", "{\"email\":\"" + contact + "\",\"name\":\"Fake contact\"}")
                .andExpect(status().isCreated());
        String job = id(postJson("/api/v1/jobs", "{\"title\":\"Fake cleanup job " + tag
                + "\",\"hiringType\":\"CLIENT_DEPLOYED\",\"clientId\":\"" + client + "\"}"));
        String created = postJson("/api/v1/jobs/" + job + "/applications",
                "{\"name\":\"Fake cleanup candidate\",\"email\":\"cleanup." + tag
                        + "@candidate.example\",\"stage\":\"SELECTED\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        application = JsonPath.read(created, "$.id");
        String candidate = JsonPath.read(created, "$.candidateId");
        document = UUID.fromString(id(mvc.perform(multipart("/api/v1/candidates/" + candidate + "/documents")
                .file(new MockMultipartFile("file", "fake-cleanup.pdf", "application/pdf", PDF))
                .param("kind", "CODEWALNUT_RESUME").with(user("admin@codewalnut.test")).with(csrf()))));
        key = tasks.find(Target.DOCUMENT, document).orElseThrow().storageKey();
        jdbc.update("UPDATE background_task SET status='READY',verified_at=? WHERE target_id=? AND target_type='DOCUMENT'",
                Timestamp.from(OLD), DocumentStorageRepository.bytes(document));
        objects.put(key, PDF);
        when(store.get(anyString())).thenAnswer(invocation -> {
            byte[] bytes = objects.get(invocation.<String>getArgument(0));
            if (bytes == null) throw new DocumentStorageUnavailableException();
            return bytes;
        });
    }

    @AfterEach
    void retainOtherTestsIndependence() {
        // Only this fixture is touched; kept legacy data must not become eligible
        // in later runs against the same persistent test database.
        if (document != null) jdbc.update("UPDATE background_task SET verified_at=? WHERE target_id=? AND target_type='DOCUMENT'",
                Timestamp.from(Instant.parse("2037-01-01T00:00:00Z")), DocumentStorageRepository.bytes(document));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).with(user("admin@codewalnut.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Map<String, Object> request() {
        Map<String, Object> request = new HashMap<>();
        request.put("confirmation", DocumentStorageCleanupService.CONFIRMATION);
        request.put("backupReference", "fake-restored-backup-" + document);
        request.put("restoreVerified", true);
        request.put("observedBefore", OLD.toString());
        return request;
    }

    private ResultActions cleanup(Map<String, Object> request) throws Exception {
        return postJson(URL, json.writeValueAsString(request));
    }

    @Test
    void DOCSTORE06_dryRunIsTheDefaultAndAuditsWithoutDeletingBytes() throws Exception {
        cleanup(request()).andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(1))
                .andExpect(jsonPath("$.cleaned").value(0));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        var record = audits.findByActionAndActorEmailOrderByCreatedAtDesc(AuditAction.DOCUMENT_STORAGE_CLEANUP, "admin@codewalnut.test").getFirst();
        assertThat(record.getDetails()).contains("\"dryRun\":true", "\"cleaned\":0").doesNotContain(key, "fake-cleanup.pdf");
    }

    @Test
    void DOCSTORE06_cleansOnlyBytesPreservesSharesAndRerunIsNoOp() throws Exception {
        mvc.perform(put("/api/v1/applications/" + application + "/client-share").with(user("admin@codewalnut.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeContact\":false,\"includeProfile\":false,\"documentIds\":[\"" + document + "\"]}"))
                .andExpect(status().isOk());
        var before = tasks.find(Target.DOCUMENT, document).orElseThrow();
        var request = request();
        request.put("dryRun", false);
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.cleaned").value(1));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isNull();
        assertThat(tasks.find(Target.DOCUMENT, document).orElseThrow()).isEqualTo(before);
        var session = (MockHttpSession) mvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + contact + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/v1/client/documents/" + document).session(session))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.scanned").value(0)).andExpect(jsonPath("$.cleaned").value(0));
        assertThat(audits.findByActionAndActorEmailOrderByCreatedAtDesc(AuditAction.DOCUMENT_STORAGE_CLEANUP, "admin@codewalnut.test"))
                .anySatisfy(audit -> {
                    assertThat(audit.getEntityId()).isEqualTo(document.toString());
                    assertThat(audit.getDetails()).contains("\"cleaned\":1", "\"restoreVerified\":true");
                });
    }

    @Test
    void DOCSTORE06_missingAndMismatchedObjectsKeepDatabaseBytes() throws Exception {
        var request = request();
        request.put("dryRun", false);
        objects.clear();
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.failed").value(1)).andExpect(jsonPath("$.cleaned").value(0));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        objects.put(key, "corrupt fake content".getBytes(StandardCharsets.US_ASCII));
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.failed").value(1));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }

    @Test
    void DOCSTORE06_rechecksCurrentSourceBytesAfterRemoteRead() throws Exception {
        byte[] changed = "%PDF-changed fake source".getBytes(StandardCharsets.US_ASCII);
        when(store.get(key)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            jdbc.update("UPDATE candidate_document SET data=? WHERE id=?", changed, DocumentStorageRepository.bytes(document));
            return PDF;
        });
        var request = request(); request.put("dryRun", false);
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.failed").value(1));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(changed);
    }

    @Test
    void DOCSTORE06_rechecksManifestAfterRemoteRead() throws Exception {
        when(store.get(key)).thenAnswer(invocation -> {
            jdbc.update("UPDATE background_task SET verified_at=? WHERE target_id=?", Timestamp.from(NOW), DocumentStorageRepository.bytes(document));
            return PDF;
        });
        var request = request(); request.put("dryRun", false);
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.skipped").value(1)).andExpect(jsonPath("$.cleaned").value(0));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }

    @Test
    void DOCSTORE06_failedAuditRollsBackTheDeletion() throws Exception {
        AuditService auditTarget = AopTestUtils.getUltimateTargetObject(auditService);
        doThrow(new IllegalStateException("fake audit failure")).when(auditTarget)
                .recordInCurrentTransaction(any(), eq(AuditAction.DOCUMENT_STORAGE_CLEANUP), anyString(), any(), any());
        var request = request(); request.put("dryRun", false);
        cleanup(request).andExpect(status().isOk()).andExpect(jsonPath("$.failed").value(1));
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }

    @Test
    void DOCSTORE06_recentCopiesAndInvalidAttestationsCannotBeCleaned() throws Exception {
        jdbc.update("UPDATE background_task SET verified_at=? WHERE target_id=?", Timestamp.from(NOW), DocumentStorageRepository.bytes(document));
        cleanup(request()).andExpect(status().isOk()).andExpect(jsonPath("$.scanned").value(0));
        for (var invalid : Map.<String, Object>of("observedBefore", NOW.toString(), "restoreVerified", false,
                "confirmation", "wrong phrase", "limit", 11, "backupReference", "https://secret.example/token").entrySet()) {
            var request = request(); request.put(invalid.getKey(), invalid.getValue());
            cleanup(request).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(store);
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }

    @Test
    void DOCSTORE06_rejectsNonAdminMissingCsrfAndViewAsBeforeStorageAccess() throws Exception {
        String body = json.writeValueAsString(request());
        mvc.perform(post(URL).with(user("recruiter@codewalnut.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(URL).with(user("admin@codewalnut.test")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/v1/admin/view-as").session(session).with(user("admin@codewalnut.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"ROLE\",\"role\":\"RECRUITER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(URL).session(session).with(user("admin@codewalnut.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(store);
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }
}
