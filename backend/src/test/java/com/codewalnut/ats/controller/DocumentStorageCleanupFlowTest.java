package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
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
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
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
    @Autowired private DataSource dataSource;
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
        retireAbandonedFixtures();
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
        doAnswer(invocation -> {
            byte[] bytes = objects.get(invocation.<String>getArgument(0));
            if (bytes == null) throw new DocumentStorageUnavailableException();
            return bytes;
        }).when(store).get(anyString());
    }

    private void retireAbandonedFixtures() {
        // A killed JVM never runs @AfterEach. Restrict repair to this class's
        // reserved synthetic filename/uploader, leaving other suites' rows alone.
        jdbc.update("UPDATE background_task t JOIN candidate_document d ON d.id=t.target_id "
                        + "SET t.verified_at=? WHERE t.target_type='DOCUMENT' AND d.file_name=? AND d.uploaded_by=?",
                Timestamp.from(Instant.parse("2037-01-01T00:00:00Z")), "fake-cleanup.pdf", "admin@codewalnut.test");
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
    void DOCSTORE06_nextSetupRepairsFixturesLeftByAnAbortedRun() throws Exception {
        UUID abandoned = document;
        objects.clear(); // A previous JVM's fake object store does not survive.
        fixture();
        assertThat(tasks.find(Target.DOCUMENT, abandoned).orElseThrow().verifiedAt()).isAfter(NOW);
        assertThat(tasks.staged(Target.DOCUMENT, abandoned)).isEqualTo(PDF);
        cleanup(request()).andExpect(status().isOk()).andExpect(jsonPath("$.scanned").value(1))
                .andExpect(jsonPath("$.eligible").value(1)).andExpect(jsonPath("$.failed").value(0));
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
    void DOCSTORE06_concurrentCleanupWaitsForMysqlLockAndDeletesExactlyOnce() throws Exception {
        CountDownLatch bothSelected = new CountDownLatch(2);
        CountDownLatch finishRemoteReads = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            bothSelected.countDown();
            if (!finishRemoteReads.await(10, TimeUnit.SECONDS)) throw new AssertionError("Remote reads not released");
            return PDF;
        }).when(store).get(key);
        var request = request();
        request.put("dryRun", false);
        try (Connection blocker = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            // A third, independent connection owns the real MySQL row lock. Both
            // HTTP requests must reach the provider before either can delete.
            try (var statement = blocker.prepareStatement("SELECT id FROM candidate_document WHERE id=? FOR UPDATE")) {
                statement.setBytes(1, DocumentStorageRepository.bytes(document));
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            var first = executor.submit(() -> cleanup(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            var second = executor.submit(() -> cleanup(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            try {
                assertThat(bothSelected.await(10, TimeUnit.SECONDS)).isTrue();
                finishRemoteReads.countDown();
                org.junit.jupiter.api.Assertions.assertThrows(TimeoutException.class, () -> first.get(200, TimeUnit.MILLISECONDS));
                org.junit.jupiter.api.Assertions.assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
                blocker.commit();
                String a = first.get(10, TimeUnit.SECONDS);
                String b = second.get(10, TimeUnit.SECONDS);
                assertThat((Integer) JsonPath.read(a, "$.cleaned") + (Integer) JsonPath.read(b, "$.cleaned")).isEqualTo(1);
                assertThat((Integer) JsonPath.read(a, "$.skipped") + (Integer) JsonPath.read(b, "$.skipped")).isEqualTo(1);
                assertThat((Integer) JsonPath.read(a, "$.failed") + (Integer) JsonPath.read(b, "$.failed")).isZero();
                assertThat(tasks.staged(Target.DOCUMENT, document)).isNull();
                long committedAudits = audits.findByActionAndActorEmailOrderByCreatedAtDesc(
                        AuditAction.DOCUMENT_STORAGE_CLEANUP, "admin@codewalnut.test").stream()
                        .filter(audit -> document.toString().equals(audit.getEntityId())).count();
                assertThat(committedAudits).isEqualTo(1);
            } finally {
                finishRemoteReads.countDown();
                blocker.rollback();
            }
        }
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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("You're viewing as "),
                        org.hamcrest.Matchers.containsString("(read-only), so nothing was changed."))));
        verifyNoInteractions(store);
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }
}
