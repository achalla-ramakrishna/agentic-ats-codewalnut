package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.BrandedResume;
import com.codewalnut.ats.client.PrivateDocumentStore;
import com.codewalnut.ats.client.ResumeAnalyzer;
import com.codewalnut.ats.client.ResumeInsight;
import com.codewalnut.ats.client.ResumeWriter;
import com.codewalnut.ats.domain.BackgroundTask;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import com.codewalnut.ats.domain.CandidateInsight;
import com.codewalnut.ats.domain.Client;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.ClientShare;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.HiringType;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.ResumeIntake;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateInsightRepository;
import com.codewalnut.ats.repository.ClientRepository;
import com.codewalnut.ats.repository.ClientContactRepository;
import com.codewalnut.ats.repository.ClientShareRepository;
import com.codewalnut.ats.repository.DocumentStorageRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.repository.ResumeIntakeRepository;
import com.codewalnut.ats.task.DocumentStorageWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real committed MySQL lifecycle; all provider calls use fake bytes and fake adapters. */
@SpringBootTest(properties = {
        "ats.document-storage.enabled=true", "ats.document-storage.operations-enabled=true",
        "ats.document-storage.poll-ms=86400000", "ats.document-storage.bridge-url=http://localhost:3001",
        "ats.document-storage.bridge-secret=fake-lifecycle-secret-at-least-32-characters"
})
class PrivateStorageLifecycleTest {
    private static final byte[] PDF = "%PDF-1.4\nObviously fake lifecycle document".getBytes(StandardCharsets.US_ASCII);
    @Autowired private ResumeIntelligenceService resumes;
    @Autowired private ResumeProcessor processor;
    @Autowired private DocumentService documents;
    @Autowired private ClientPortalService clientPortal;
    @Autowired private CodeWalnutResumeService brandedResumes;
    @Autowired private ApplicationRepository applications;
    @Autowired private ClientRepository clients;
    @Autowired private ClientContactRepository contacts;
    @Autowired private ClientShareRepository shares;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ResumeIntakeRepository intakes;
    @Autowired private CandidateInsightRepository insights;
    @Autowired private JobOpeningRepository jobs;
    @Autowired private AppUserRepository users;
    @Autowired private DocumentStorageWorker worker;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ObjectMapper json;
    @SpyBean private DocumentStorageRepository tasks;
    @MockBean private PrivateDocumentStore store;
    @MockBean private ResumeAnalyzer analyzer;
    @MockBean private ResumeWriter writer;
    private final String tag = UUID.randomUUID().toString();

    @BeforeEach
    void fakeAnalyzer() {
        when(analyzer.available()).thenReturn(true);
        when(analyzer.model()).thenReturn("fake-lifecycle-analyzer");
        when(analyzer.analyze(any(), any())).thenAnswer(invocation -> {
            ResumeAnalyzer.ResumeFile file = invocation.getArgument(1);
            assertThat(file.data()).isEqualTo(PDF);
            return json.convertValue(Map.of("name", "Fake lifecycle candidate", "email", "lifecycle." + tag + "@example.test",
                    "skills", List.of(), "experience", List.of(), "projects", List.of(), "requirements", List.of(),
                    "strengths", List.of(), "gaps", List.of(), "questionsToAsk", List.of()), ResumeInsight.class);
        });
        // Even an accidental manual poll must never process another test's persisted queue.
        doReturn(List.of()).when(tasks).due();
    }

    private ResumeIntake upload() throws Exception {
        JobOpening job = jobs.save(JobOpening.builder().title("Fake lifecycle job " + tag).hiringType(HiringType.INTERNAL).build());
        resumes.upload(users.findByEmail("admin@codewalnut.test").orElseThrow(), job.getId(), List.of(
                new MockMultipartFile("files", "fake-lifecycle.pdf", "application/pdf", PDF)));
        UUID intake = intakes.findByJobIdAndCreatedAtAfterOrderByCreatedAtAsc(job.getId(), Instant.EPOCH).getFirst().getId();
        return intakes.findById(intake).orElseThrow();
    }

    private UUID document(ResumeIntake intake) {
        return insights.findByApplicationId(intake.getApplicationId()).orElseThrow().getDocumentId();
    }

    private BackgroundTask blobOnly(ResumeIntake intake) {
        UUID document = document(intake);
        var manifest = tasks.find(Target.DOCUMENT, document).orElseThrow();
        jdbc.update("UPDATE background_task SET status='READY',verified_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                DocumentStorageRepository.bytes(manifest.id()));
        jdbc.update("UPDATE candidate_document SET data=NULL WHERE id=?", DocumentStorageRepository.bytes(document));
        return tasks.find(Target.DOCUMENT, document).orElseThrow();
    }

    private void assertDatabaseReleased() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory)).isFalse();
        // A suspended transaction would hide its bindings but still occupy the pool.
        assertThat(((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    private void remoteBytesWithoutDatabaseConnection(BackgroundTask manifest) {
        when(store.get(manifest.storageKey())).thenAnswer(invocation -> {
            assertDatabaseReleased();
            return PDF;
        });
    }

    @Test
    void DOCSTORE02_staffDownloadReleasesDatabaseBeforeReadingBlobOnlyBytes() throws Exception {
        var manifest = blobOnly(upload());
        remoteBytesWithoutDatabaseConnection(manifest);
        var download = documents.download(users.findByEmail("admin@codewalnut.test").orElseThrow(), manifest.targetId());
        assertThat(download.data()).isEqualTo(PDF);
        verify(store).get(manifest.storageKey());
    }

    @Test
    void DOCSTORE02_clientDownloadFinishesShareAuthorizationBeforeReadingBlobOnlyBytes() throws Exception {
        ResumeIntake intake = upload();
        var manifest = blobOnly(intake);
        Client client = clients.save(Client.builder().name("Fake lifecycle client " + tag).build());
        ClientContact contact = contacts.save(ClientContact.builder().client(client).email("client." + tag + "@example.test")
                .name("Fake lifecycle client").addedBy("admin@codewalnut.test").build());
        shares.save(ClientShare.builder().client(client).application(applications.findById(intake.getApplicationId()).orElseThrow())
                .documentIds(Set.of(manifest.targetId())).sharedBy("admin@codewalnut.test").sharedAt(Instant.now()).build());
        remoteBytesWithoutDatabaseConnection(manifest);
        assertThat(clientPortal.download(contact, manifest.targetId()).data()).isEqualTo(PDF);
        verify(store).get(manifest.storageKey());
    }

    @Test
    void DOCSTORE02_insightReleasesItsPendingStateTransactionBeforeRemoteRead() throws Exception {
        ResumeIntake intake = upload();
        var manifest = blobOnly(intake);
        remoteBytesWithoutDatabaseConnection(manifest);
        processor.markPending(intake.getApplicationId());
        resumes.processInsight(intake.getApplicationId());
        assertThat(insights.findByApplicationId(intake.getApplicationId()).orElseThrow().getStatus()).isEqualTo(CandidateInsight.Status.DONE);
        verify(store).get(manifest.storageKey());
    }

    @Test
    void DOCSTORE02_brandedDraftReadsStorageAndCallsWriterWithoutHoldingDatabaseConnection() throws Exception {
        ResumeIntake intake = upload();
        var manifest = blobOnly(intake);
        remoteBytesWithoutDatabaseConnection(manifest);
        when(writer.write(any(), any())).thenAnswer(invocation -> {
            assertDatabaseReleased();
            assertThat(invocation.<ResumeAnalyzer.ResumeFile>getArgument(1).data()).isEqualTo(PDF);
            return new BrandedResume("Fake lifecycle candidate", "Fake role", "", "", "", "Fake summary", List.of(), List.of());
        });
        var draft = brandedResumes.generate(users.findByEmail("admin@codewalnut.test").orElseThrow(), intake.getApplicationId());
        assertThat(draft.exists()).isTrue();
        assertThat(draft.sourceFileName()).isEqualTo("fake-lifecycle.pdf");
        verify(store).get(manifest.storageKey());
    }

    @Test
    void DOCSTORE02_brandedDraftRefusesToSaveIfOriginalChangedDuringRemoteWork() throws Exception {
        ResumeIntake intake = upload();
        var manifest = blobOnly(intake);
        remoteBytesWithoutDatabaseConnection(manifest);
        var candidateId = applications.findById(intake.getApplicationId()).orElseThrow().getCandidate().getId();
        when(writer.write(any(), any())).thenAnswer(invocation -> {
            assertDatabaseReleased();
            new TransactionTemplate(transactions).execute(status -> documents.store(candidateId, DocumentKind.ORIGINAL_RESUME,
                    "fake-replacement.pdf", PDF, "admin@codewalnut.test"));
            return new BrandedResume("Fake candidate", "Fake role", "", "", "", "Fake summary", List.of(), List.of());
        });
        assertThatThrownBy(() -> brandedResumes.generate(users.findByEmail("admin@codewalnut.test").orElseThrow(), intake.getApplicationId()))
                .isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM codewalnut_resume WHERE application_id=?", Long.class,
                DocumentStorageRepository.bytes(intake.getApplicationId()))).isZero();
    }

    @Test
    void DOCSTORE04_successfulBulkIntakeClearsTemporaryBytesAndQueuesOnlyTheFinalDocument() throws Exception {
        ResumeIntake intake = upload();
        assertThat(intake.getStatus()).isEqualTo(ResumeIntake.Status.DONE);
        assertThat(intake.getData()).isNull();
        assertThat(tasks.find(Target.INTAKE, intake.getId())).isEmpty();
        UUID document = document(intake);
        assertThat(tasks.find(Target.DOCUMENT, document).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        verify(analyzer).analyze(any(), any());
        verifyNoInteractions(store);
    }

    @Test
    void DOCSTORE04_failedBulkIntakeClearsTemporaryBytesWithoutCreatingPrivateObjects() throws Exception {
        doThrow(new CalendarException("Fake analyzer unavailable")).when(analyzer).analyze(any(), any());
        ResumeIntake intake = upload();
        assertThat(intake.getStatus()).isEqualTo(ResumeIntake.Status.FAILED);
        assertThat(intake.getData()).isNull();
        assertThat(intake.getApplicationId()).isNull();
        assertThat(intake.getError()).isEqualTo("Fake analyzer unavailable");
        assertThat(tasks.find(Target.INTAKE, intake.getId())).isEmpty();
        verifyNoInteractions(store);
    }

    @Test
    void DOCSTORE02_blobOnlyReadFailureFinishesInsightAsFailedInsteadOfLeavingPending() throws Exception {
        ResumeIntake intake = upload();
        UUID document = document(intake);
        var manifest = tasks.find(Target.DOCUMENT, document).orElseThrow();
        jdbc.update("UPDATE background_task SET status='READY',verified_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                DocumentStorageRepository.bytes(manifest.id()));
        jdbc.update("UPDATE candidate_document SET data=NULL WHERE id=?", DocumentStorageRepository.bytes(document));
        when(store.get(manifest.storageKey())).thenThrow(new DocumentStorageUnavailableException());
        processor.markPending(intake.getApplicationId());
        clearInvocations(analyzer);
        resumes.processInsight(intake.getApplicationId());
        var result = insights.findByApplicationId(intake.getApplicationId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(CandidateInsight.Status.FAILED);
        assertThat(result.getError()).isNotBlank();
        verify(store).get(manifest.storageKey());
        verifyNoInteractions(analyzer);
    }

    @Test
    void DOCSTORE03_committedWorkerTransfersOutsideTransactionsAndRetainsStagedBytes() throws Exception {
        UUID document = document(upload());
        BackgroundTask manifest = tasks.find(Target.DOCUMENT, document).orElseThrow();
        doReturn(List.of(manifest)).when(tasks).due();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(invocation.<byte[]>getArgument(1)).isEqualTo(PDF);
            assertThat(invocation.<String>getArgument(2)).isEqualTo(DocumentContentService.sha256(PDF));
            return null;
        }).when(store).put(anyString(), any(byte[].class), anyString());
        when(store.get(manifest.storageKey())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return PDF;
        });
        worker.poll();
        var complete = tasks.find(Target.DOCUMENT, document).orElseThrow();
        assertThat(complete.status()).isEqualTo("READY");
        assertThat(complete.verifiedAt()).isNotNull();
        assertThat(tasks.staged(Target.DOCUMENT, document)).isEqualTo(PDF);
        verify(store).put(manifest.storageKey(), PDF, manifest.sha256());
        verify(store).get(manifest.storageKey());
    }

    @Test
    void DOCSTORE03_preexistingIntakeTaskIsSkippedWithoutAnyProviderCall() throws Exception {
        ResumeIntake intake = upload();
        UUID taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO background_task(id,target_type,target_id,storage_key,sha256,size_bytes) VALUES(?,?,?,?,?,?)",
                DocumentStorageRepository.bytes(taskId), "INTAKE", DocumentStorageRepository.bytes(intake.getId()),
                Target.INTAKE.key(intake.getId()), DocumentContentService.sha256(PDF), PDF.length);
        // Simulate an old release's task while its intake still has temporary bytes.
        jdbc.update("UPDATE resume_intake SET data=? WHERE id=?", PDF, DocumentStorageRepository.bytes(intake.getId()));
        try {
            doReturn(List.of(tasks.find(Target.INTAKE, intake.getId()).orElseThrow())).when(tasks).due();
            worker.poll();
            assertThat(tasks.find(Target.INTAKE, intake.getId()).orElseThrow().status()).isEqualTo("SKIPPED");
            assertThat(tasks.staged(Target.INTAKE, intake.getId())).isEqualTo(PDF);
            verifyNoInteractions(store);
        } finally {
            jdbc.update("UPDATE resume_intake SET data=NULL WHERE id=?", DocumentStorageRepository.bytes(intake.getId()));
        }
    }

    @Test
    void DOCSTORE03_independentConnectionsClaimOnceAndStaleLeasesCannotCompleteWork() throws Exception {
        UUID document = document(upload());
        BackgroundTask manifest = tasks.find(Target.DOCUMENT, document).orElseThrow();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<Long> connections = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var contenders = List.of(first, second).stream().map(token -> executor.submit(() ->
                    new TransactionTemplate(transactions).execute(status -> {
                        connections.add(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                        ready.countDown();
                        try { assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                        return tasks.claim(manifest.id(), token);
                    }))).toList();
            boolean firstWon = contenders.get(0).get(10, TimeUnit.SECONDS);
            boolean secondWon = contenders.get(1).get(10, TimeUnit.SECONDS);
            assertThat(connections).hasSize(2);
            assertThat(firstWon ^ secondWon).isTrue();
            UUID winner = firstWon ? first : second;
            UUID loser = firstWon ? second : first;
            tasks.complete(manifest.id(), loser, false);
            assertThat(tasks.find(Target.DOCUMENT, document).orElseThrow().status()).isEqualTo("PENDING");
            jdbc.update("UPDATE background_task SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE id=?",
                    DocumentStorageRepository.bytes(manifest.id()));
            UUID recovered = UUID.randomUUID();
            assertThat(tasks.claim(manifest.id(), recovered)).isTrue();
            tasks.complete(manifest.id(), winner, false);
            assertThat(tasks.find(Target.DOCUMENT, document).orElseThrow().status()).isEqualTo("PENDING");
            tasks.complete(manifest.id(), recovered, false);
            assertThat(tasks.find(Target.DOCUMENT, document).orElseThrow().status()).isEqualTo("READY");
        }
        verifyNoInteractions(store);
    }
}
