package com.codewalnut.ats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.codewalnut.ats.config.DocumentStorageProperties;
import com.codewalnut.ats.service.DocumentContentService;
import com.codewalnut.ats.service.DocumentStorageUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlobBridgeDocumentStoreTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private final String key = "documents/" + UUID.randomUUID();
    private final byte[] pdf = "%PDF-1.4 fake fixture".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); executor.shutdownNow(); }

    private BlobBridgeDocumentStore store(Duration timeout) {
        return new BlobBridgeDocumentStore(new DocumentStorageProperties(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), "fake-bridge-secret-for-tests-00000000", true), timeout);
    }

    @Test
    void DOCSTORE_02_adapterSendsBoundedImmutablePutAndReadsExactBytes() {
        AtomicInteger puts = new AtomicInteger();
        server.createContext("/objects/" + key, exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fake-bridge-secret-for-tests-00000000");
            byte[] output = pdf;
            if (exchange.getRequestMethod().equals("PUT")) {
                assertThat(exchange.getRequestHeaders().getFirst("X-Content-SHA256")).isEqualTo(DocumentContentService.sha256(pdf));
                assertThat(exchange.getRequestBody().readAllBytes()).isEqualTo(pdf);
                puts.incrementAndGet();
                output = "{}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
            exchange.close();
        });
        var store = store(Duration.ofSeconds(5));
        store.put(key, pdf, DocumentContentService.sha256(pdf));
        assertThat(puts.get()).isEqualTo(1);
        assertThat(store.get(key)).isEqualTo(pdf);
    }

    @Test
    void DOCSTORE_02_completeBodyDeadlineCancelsStalledResponseAfterHeaders() {
        CountDownLatch headersSent = new CountDownLatch(1);
        server.createContext("/objects/" + key, exchange -> {
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write(1);
            exchange.getResponseBody().flush();
            headersSent.countDown();
            try { new CountDownLatch(1).await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            assertThatThrownBy(() -> store(Duration.ofMillis(500)).get(key))
                    .isInstanceOf(DocumentStorageUnavailableException.class);
            assertThat(headersSent.getCount()).isZero();
        });
    }

    @Test
    void DOCSTORE_02_oversizedResponseAndRedirectAreRejectedWithoutFollowing() {
        AtomicInteger forbidden = new AtomicInteger();
        server.createContext("/private-secret", exchange -> { forbidden.incrementAndGet(); exchange.close(); });
        server.createContext("/objects/" + key, exchange -> {
            exchange.getResponseHeaders().add("Location", "/private-secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        assertThatThrownBy(() -> store(Duration.ofSeconds(3)).get(key)).isInstanceOf(DocumentStorageUnavailableException.class);
        assertThat(forbidden.get()).isZero();
        server.removeContext("/objects/" + key);
        server.createContext("/objects/" + key, exchange -> {
            byte[] large = new byte[10 * 1024 * 1024 + 1];
            exchange.sendResponseHeaders(200, large.length);
            try { exchange.getResponseBody().write(large); }
            finally { exchange.close(); }
        });
        assertThatThrownBy(() -> store(Duration.ofSeconds(3)).get(key)).isInstanceOf(DocumentStorageUnavailableException.class);
    }

    @Test
    void DOCSTORE_01_arbitraryUrlsAndDisabledStorageNeverReachBridge() {
        assertThatThrownBy(() -> store(Duration.ofSeconds(1)).get("https://public.example/file"))
                .isInstanceOf(DocumentStorageUnavailableException.class);
        var disabled = new BlobBridgeDocumentStore(new DocumentStorageProperties(false, "", "", false));
        assertThatThrownBy(() -> disabled.get(key)).isInstanceOf(DocumentStorageUnavailableException.class);
        assertThatThrownBy(() -> new BlobBridgeDocumentStore(new DocumentStorageProperties(true,
                "https://bridge.example/?token=bad", "fake-test-bridge-secret-long-enough", true)))
                .isInstanceOf(IllegalStateException.class);
    }
}
