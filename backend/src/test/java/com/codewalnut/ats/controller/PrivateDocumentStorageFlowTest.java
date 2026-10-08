package com.codewalnut.ats.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.domain.BackgroundTask;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import com.codewalnut.ats.service.DocumentContentService;
import com.codewalnut.ats.service.DocumentStorageUnavailableException;
import com.codewalnut.ats.task.DocumentStorageWorker;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL outbox and authorization; only the external private object store is faked. */
@SpringBootTest(properties = {
        "ats.document-storage.enabled=true",
        "ats.document-storage.operations-enabled=true",
        "ats.document-storage.bridge-url=http://localhost:3001",
        "ats.document-storage.bridge-secret=fake-test-bridge-secret-at-least-32-characters",
        "ats.document-storage.poll-ms=86400000"
})
@AutoConfigureMockMvc
@Transactional
class PrivateDocumentStorageFlowTest {
    private static final RequestPostProcessor ADMIN = user("admin@codewalnut.test");
    private static final RequestPostProcessor MANAGER = user("hiring.manager@codewalnut.test");
    private static final byte[] PDF = "%PDF-1.4\nObviously fake private resume".getBytes(StandardCharsets.US_ASCII);
    private static final String OPERATIONS = "/api/v1/admin/document-storage";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentStorageRepository tasks;
    @Autowired private DocumentStorageWorker worker;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private ApplicationContext context;
    @Autowired private ScheduledAnnotationBeanPostProcessor scheduledTasks;
    @MockBean private PrivateDocumentStore store;

    private final String tag = UUID.randomUUID().toString();
    private final Map<String, byte[]> objects = new HashMap<>();
    private final Set<String> failAfterPut = new HashSet<>();

    @BeforeEach
    void fakePrivateStorage() {
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            byte[] bytes = invocation.getArgument(1);
            String checksum = invocation.getArgument(2);
            assertThat(DocumentContentService.sha256(bytes)).isEqualTo(checksum);
            byte[] existing = objects.putIfAbsent(key, Arrays.copyOf(bytes, bytes.length));
            if (existing != null && !Arrays.equals(existing, bytes)) throw new DocumentStorageUnavailableException();
            if (failAfterPut.contains(key)) throw new DocumentStorageUnavailableException();
            return null;
        }).when(store).put(anyString(), any(byte[].class), anyString());
        when(store.get(anyString())).thenAnswer(invocation -> {
            byte[] bytes = objects.get(invocation.<String>getArgument(0));
            if (bytes == null) throw new DocumentStorageUnavailableException();
            return Arrays.copyOf(bytes, bytes.length);
        });
    }

    private record Fixture(String clientId, String contactEmail, String applicationId, String candidateId) {}

    private Fixture fixture(String suffix) throws Exception {
        String client = id(mockMvc.perform(post("/api/v1/clients").with(ADMIN).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fake storage " + tag + suffix + "\"}")));
        String contact = "storage." + tag + suffix + "@client.example";
        mockMvc.perform(post("/api/v1/clients/" + client + "/contacts").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + contact + "\",\"name\":\"Fake client contact\"}"))
                .andExpect(status().isCreated());
        String job = id(mockMvc.perform(post("/api/v1/jobs").with(ADMIN).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Fake storage job " + tag + suffix
                        + "\",\"hiringType\":\"CLIENT_DEPLOYED\",\"clientId\":\"" + client + "\"}")));
        String response = mockMvc.perform(post("/api/v1/jobs/" + job + "/applications").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fake storage candidate\",\"email\":\"storage."
                                + tag + suffix + "@candidate.example\",\"stage\":\"SELECTED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Fixture(client, contact, JsonPath.read(response, "$.id"), JsonPath.read(response, "$.candidateId"));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID upload(Fixture fixture, String kind) throws Exception {
        return UUID.fromString(id(mockMvc.perform(multipart("/api/v1/candidates/" + fixture.candidateId() + "/documents")
                .file(new MockMultipartFile("file", "fake-document.pdf", "application/pdf", PDF))
                .param("kind", kind).with(ADMIN).with(csrf()))));
    }

    private BackgroundTask task(UUID document) {
        return tasks.find(Target.DOCUMENT, document).orElseThrow();
    }

    private void pollUntilProgress(UUID document) {
        // A shared test MySQL may already contain queued fixtures. Do not assume
        // an empty queue or mutate those rows to make this fixture go first.
        long maxPolls = tasks.counts().getOrDefault("PENDING", 0L) + 1;
        for (long i = 0; i < maxPolls; i++) {
            worker.poll();
            BackgroundTask current = task(document);
            if (!"PENDING".equals(current.status()) || current.attempts() > 0) return;
        }
        throw new AssertionError("Fixture never processed");
    }

    private void migrateAndRemoveLegacyCopy(UUID document) {
        pollUntilProgress(document);
        assertThat(task(document).status()).isEqualTo("READY");
        assertThat(task(document).verifiedAt()).isNotNull();
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        // Simulate the separately gated cleanup, confined to this rolled-back fixture.
        jdbc.update("UPDATE candidate_document SET data=NULL WHERE id=?", DocumentStorageRepository.bytes(document));
        entityManager.clear();
        assertThat(tasks.staged(Target.DOCUMENT, document)).isNull();
        clearInvocations(store);
    }

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/v1/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    @Test
    void DOCSTORE03_storageWorkerRemainsScheduledWhenCodingAsyncIsDisabled() {
        assertThat(context.getEnvironment().getProperty("ats.coding.async", Boolean.class)).isFalse();
        assertThat(context.getBean("documentStorageScheduler")).isNotSameAs(context.getBean("taskScheduler"));
        var storageSchedules = scheduledTasks.getScheduledTasks().stream()
                .map(scheduled -> scheduled.getTask().getRunnable())
                .filter(ScheduledMethodRunnable.class::isInstance)
                .map(ScheduledMethodRunnable.class::cast)
                .filter(runnable -> runnable.getTarget() == worker && runnable.getMethod().getName().equals("poll"))
                .toList();
        assertThat(storageSchedules).hasSize(1);
        assertThat(storageSchedules.getFirst().getQualifier()).isEqualTo("documentStorageScheduler");
    }

    @Test
    void DOCSTORE03_uploadStagesBytesAndOutboxBeforeAnyProviderCall() throws Exception {
        UUID document = upload(fixture("upload"), "CODEWALNUT_RESUME");
        BackgroundTask queued = task(document);
        assertThat(queued.status()).isEqualTo("PENDING");
        assertThat(queued.storageKey()).isEqualTo("documents/" + document);
        assertThat(queued.sha256()).isEqualTo(DocumentContentService.sha256(PDF));
        assertThat(queued.sizeBytes()).isEqualTo(PDF.length);
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        verifyNoInteractions(store);
        mockMvc.perform(get("/api/v1/documents/" + document).with(ADMIN))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        verifyNoInteractions(store);
        migrateAndRemoveLegacyCopy(document);
        mockMvc.perform(get("/api/v1/documents/" + document).with(ADMIN))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(store).get(queued.storageKey());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void DOCSTORE03_uploadAndOutboxRollBackTogether() throws Exception {
        Fixture fixture = fixture("rollback");
        AtomicReference<UUID> document = new AtomicReference<>();
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            try {
                document.set(upload(fixture, "CODEWALNUT_RESUME"));
                assertThat(task(document.get()).status()).isEqualTo("PENDING");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            throw new IllegalStateException("deliberate fixture rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("deliberate fixture rollback");
        assertThat(document.get()).isNotNull();
        assertThat(tasks.find(Target.DOCUMENT, document.get())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_document WHERE id=?", Long.class,
                DocumentStorageRepository.bytes(document.get()))).isZero();
        verifyNoInteractions(store);
    }

    @Test
    void DOCSTORE01_blobBackedGovernmentIdsStillRequireIdPermission() throws Exception {
        UUID document = upload(fixture("id"), "AADHAAR");
        migrateAndRemoveLegacyCopy(document);
        mockMvc.perform(get("/api/v1/documents/" + document).with(MANAGER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/documents/" + document).with(user("interviewer@codewalnut.test")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(store);
        mockMvc.perform(get("/api/v1/documents/" + document).with(ADMIN))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(store).get(task(document).storageKey());
    }

    @Test
    void DOCSTORE01_clientShareChecksRunBeforePrivateStorageReads() throws Exception {
        Fixture first = fixture("first");
        Fixture other = fixture("other");
        UUID shared = upload(first, "CODEWALNUT_RESUME");
        UUID unshared = upload(first, "DEGREE_CERTIFICATE");
        migrateAndRemoveLegacyCopy(shared);
        migrateAndRemoveLegacyCopy(unshared);
        mockMvc.perform(put("/api/v1/applications/" + first.applicationId() + "/client-share").with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"includeContact\":false,\"includeProfile\":false,\"documentIds\":[\"" + shared + "\"]}"))
                .andExpect(status().isOk());
        MockHttpSession allowed = signIn(first.contactEmail());
        MockHttpSession denied = signIn(other.contactEmail());
        clearInvocations(store);
        mockMvc.perform(get("/api/v1/client/documents/" + shared).session(denied)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/client/documents/" + unshared).session(allowed)).andExpect(status().isNotFound());
        verifyNoInteractions(store);
        mockMvc.perform(get("/api/v1/client/documents/" + shared).session(allowed))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(store).get(task(shared).storageKey());
        mockMvc.perform(delete("/api/v1/applications/" + first.applicationId() + "/client-share").with(ADMIN).with(csrf()))
                .andExpect(status().isOk());
        clearInvocations(store);
        mockMvc.perform(get("/api/v1/client/documents/" + shared).session(allowed)).andExpect(status().isNotFound());
        verifyNoInteractions(store);
    }

    @Test
    void DOCSTORE02_corruptRemoteCopyFallsBackOnlyToVerifiedLegacyBytes() throws Exception {
        UUID document = upload(fixture("checksum"), "CODEWALNUT_RESUME");
        pollUntilProgress(document);
        assertThat(task(document).status()).isEqualTo("READY");
        objects.put(task(document).storageKey(), "corrupt remote fake bytes".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(get("/api/v1/documents/" + document).with(ADMIN))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF));
        jdbc.update("UPDATE candidate_document SET data=NULL WHERE id=?", DocumentStorageRepository.bytes(document));
        entityManager.clear();
        mockMvc.perform(get("/api/v1/documents/" + document).with(ADMIN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Document storage is temporarily unavailable. Please try again."));
    }

    @Test
    void DOCSTORE03_ambiguousUploadRetainsBytesAndRetriesTheSameImmutableKey() throws Exception {
        UUID document = upload(fixture("retry"), "CODEWALNUT_RESUME");
        String key = task(document).storageKey();
        failAfterPut.add(key);
        pollUntilProgress(document);
        assertThat(task(document).status()).isEqualTo("PENDING");
        assertThat(task(document).attempts()).isEqualTo(1);
        assertThat(task(document).verifiedAt()).isNull();
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        assertThat(objects.get(key)).isEqualTo(PDF);
        failAfterPut.remove(key);
        jdbc.update("UPDATE background_task SET run_after=CURRENT_TIMESTAMP(6) WHERE id=?",
                DocumentStorageRepository.bytes(task(document).id()));
        worker.poll();
        assertThat(task(document).status()).isEqualTo("READY");
        assertThat(task(document).storageKey()).isEqualTo(key);
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
    }

    @Test
    void DOCSTORE04_legacyMigrationIsBoundedResumableAndAdminOnly() throws Exception {
        UUID document = upload(fixture("legacy"), "CODEWALNUT_RESUME");
        jdbc.update("DELETE FROM background_task WHERE id=?", DocumentStorageRepository.bytes(task(document).id()));
        assertThat(tasks.find(Target.DOCUMENT, document)).isEmpty();
        mockMvc.perform(get(OPERATIONS).with(MANAGER)).andExpect(status().isForbidden());
        mockMvc.perform(post(OPERATIONS + "/migrate").with(MANAGER).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(OPERATIONS + "/retry").with(MANAGER).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(OPERATIONS + "/migrate").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(post(OPERATIONS + "/retry").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(post(OPERATIONS + "/migrate?limit=101").with(ADMIN).with(csrf())).andExpect(status().isBadRequest());
        mockMvc.perform(get(OPERATIONS).with(ADMIN)).andExpect(status().isOk());
        long batches = tasks.counts().getOrDefault("UNQUEUED_DOCUMENT", 0L) / 100 + 2;
        for (long i = 0; i < batches && tasks.find(Target.DOCUMENT, document).isEmpty(); i++) {
            mockMvc.perform(post(OPERATIONS + "/migrate?limit=100").with(ADMIN).with(csrf())).andExpect(status().isOk());
        }
        UUID taskId = task(document).id();
        mockMvc.perform(post(OPERATIONS + "/migrate?limit=100").with(ADMIN).with(csrf())).andExpect(status().isOk());
        assertThat(task(document).id()).isEqualTo(taskId);
        assertThat(task(document).sha256()).isEqualTo(DocumentContentService.sha256(PDF));
        verifyNoInteractions(store);
        mockMvc.perform(post(OPERATIONS + "/retry").with(ADMIN).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void DOCSTORE04_viewAsCannotStartMigrationOrRetryTransfers() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/admin/view-as").session(session).with(ADMIN).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"ROLE\",\"role\":\"RECRUITER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(OPERATIONS + "/migrate").session(session).with(ADMIN).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(OPERATIONS + "/retry").session(session).with(ADMIN).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
}
