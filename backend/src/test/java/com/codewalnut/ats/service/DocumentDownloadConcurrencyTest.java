package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.CandidateDocument;
import com.codewalnut.ats.domain.Client;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.ClientShare;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.HiringType;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.DocumentDownload;
import com.codewalnut.ats.repository.AppUserRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.ClientContactRepository;
import com.codewalnut.ats.repository.ClientRepository;
import com.codewalnut.ats.repository.ClientShareRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Synchronize two actual MySQL snapshots so nested audit transactions cannot borrow a third connection. */
@SpringBootTest(properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1000",
        "ats.document-storage.enabled=false"
})
class DocumentDownloadConcurrencyTest {
    private static final byte[] PDF = "%PDF-1.4\nFake concurrent download".getBytes(StandardCharsets.US_ASCII);
    @Autowired private DocumentService documents;
    @Autowired private ClientPortalService portal;
    @Autowired private CandidateRepository candidates;
    @Autowired private AppUserRepository users;
    @Autowired private ApplicationRepository applications;
    @Autowired private JobOpeningRepository jobs;
    @Autowired private ClientRepository clients;
    @Autowired private ClientContactRepository contacts;
    @Autowired private ClientShareRepository shares;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;
    @SpyBean private CandidateDocumentRepository storedDocuments;

    private CandidateDocument fixture() {
        var candidate = candidates.save(Candidate.builder().name("Fake concurrent candidate")
                .email("concurrent." + UUID.randomUUID() + "@example.test").build());
        return storedDocuments.save(CandidateDocument.builder().candidateId(candidate.getId())
                .kind(DocumentKind.ORIGINAL_RESUME).fileName("fake-concurrent.pdf").contentType("application/pdf")
                .sizeBytes(PDF.length).data(PDF).uploadedBy("admin@codewalnut.test").build());
    }

    @Test
    void DOCSTORE01_staffDownloadsAuditWithoutNestedConnectionsInSmallPool() throws Exception {
        var document = fixture();
        var actor = users.findByEmail("admin@codewalnut.test").orElseThrow();
        concurrentDownloads(document.getId(), () -> documents.download(actor, document.getId()));
    }

    @Test
    void DOCSTORE01_clientDownloadsAuditWithoutNestedConnectionsInSmallPool() throws Exception {
        var document = fixture();
        var client = clients.save(Client.builder().name("Fake concurrent client " + UUID.randomUUID()).build());
        var contact = contacts.save(ClientContact.builder().client(client).name("Fake contact")
                .email("concurrent." + UUID.randomUUID() + "@example.test").addedBy("admin@codewalnut.test").build());
        var job = jobs.save(JobOpening.builder().title("Fake concurrent job").hiringType(HiringType.INTERNAL).build());
        var application = applications.save(Application.builder().job(job)
                .candidate(candidates.findById(document.getCandidateId()).orElseThrow()).stage(Stage.SELECTED).build());
        shares.save(ClientShare.builder().client(client).application(application).documentIds(Set.of(document.getId()))
                .sharedBy("admin@codewalnut.test").sharedAt(Instant.now()).build());
        concurrentDownloads(document.getId(), () -> portal.download(contact, document.getId()));
    }

    private void concurrentDownloads(UUID documentId, Callable<DocumentDownload> download) throws Exception {
        CountDownLatch snapshots = new CountDownLatch(2);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            // Use the real thread-bound JPA query while inserting a barrier before the repository returns.
            var result = Optional.ofNullable(entityManager.find(CandidateDocument.class, documentId));
            // The real query has acquired a connection owned by the service transaction.
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (reads.getAndIncrement() < 2) {
                snapshots.countDown();
                assertThat(snapshots.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(storedDocuments).findById(eq(documentId));

        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Future<DocumentDownload>> requests = new ArrayList<>();
            for (int i = 0; i < 3; i++) requests.add(executor.submit(download));
            for (var request : requests) assertThat(request.get(8, TimeUnit.SECONDS).data()).isEqualTo(PDF);
        }
        assertThat(reads.get()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='DOCUMENT_DOWNLOADED' "
                + "AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.documentId'))=?", Long.class, documentId.toString())).isEqualTo(3);
    }
}
