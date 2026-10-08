package com.codewalnut.ats.client;

import com.codewalnut.ats.config.DocumentStorageProperties;
import com.codewalnut.ats.service.DocumentStorageUnavailableException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Uses the internal bridge's official Vercel SDK, not an undocumented provider upload API. */
@Component
public class BlobBridgeDocumentStore implements PrivateDocumentStore {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Pattern KEY = Pattern.compile("(documents|intakes)/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private final DocumentStorageProperties properties;
    private final HttpClient client;
    private final Duration timeout;

    @Autowired
    public BlobBridgeDocumentStore(DocumentStorageProperties properties) {
        this(properties, Duration.ofSeconds(60));
    }

    BlobBridgeDocumentStore(DocumentStorageProperties properties, Duration timeout) {
        this.properties = properties;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        if (properties.enabled()) {
            URI uri = URI.create(properties.bridgeUrl() == null ? "" : properties.bridgeUrl());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || properties.bridgeSecret() == null || properties.bridgeSecret().length() < 32
                    || properties.bridgeSecret().chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalStateException("Private document storage requires a bridge origin and a 32-character secret");
            }
        }
    }

    private HttpRequest.Builder request(String key) {
        if (!properties.enabled() || !KEY.matcher(key).matches()) throw new DocumentStorageUnavailableException();
        return HttpRequest.newBuilder(URI.create(properties.bridgeUrl().replaceAll("/$", "") + "/objects/" + key))
                .timeout(timeout).header("Authorization", "Bearer " + properties.bridgeSecret());
    }

    @Override
    public void put(String key, byte[] bytes, String sha256) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new DocumentStorageUnavailableException();
        exchange(request(key).header("Content-Type", "application/octet-stream")
                .header("X-Content-SHA256", sha256).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), 1024);
    }

    @Override
    public byte[] get(String key) {
        byte[] bytes = exchange(request(key).GET().build(), MAX_BYTES);
        if (bytes.length == 0) throw new DocumentStorageUnavailableException();
        return bytes;
    }

    private byte[] exchange(HttpRequest request, int limit) {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpResponse<byte[]> response = exchangeOnce(request, limit, deadline);
            if (response.statusCode() == 200) return response.body();
            // Only retry explicit transient read failures. Uploads already have a durable
            // outbox; redirects, auth failures and corrupt bodies must never be retried here.
            if (!request.method().equals("GET") || response.statusCode() != 503 || attempt == 2) {
                throw new DocumentStorageUnavailableException();
            }
            long delayMillis = 200L << attempt;
            try {
                long retryAfter = Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"));
                if (retryAfter > 0) delayMillis = Math.max(delayMillis, Math.min(retryAfter, 2) * 1000);
            } catch (NumberFormatException ignored) {
                // The internal bridge uses delta seconds; malformed headers use local backoff.
            }
            if (deadline - System.nanoTime() <= TimeUnit.MILLISECONDS.toNanos(delayMillis)) {
                throw new DocumentStorageUnavailableException();
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DocumentStorageUnavailableException();
            }
        }
        throw new DocumentStorageUnavailableException();
    }

    private HttpResponse<byte[]> exchangeOnce(HttpRequest request, int limit, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new DocumentStorageUnavailableException();
        BoundedBody body = new BoundedBody(limit);
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, ignored -> body);
        try {
            // One deadline covers all retries and complete bodies, including a stalled peer.
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentStorageUnavailableException();
        } catch (Exception e) {
            throw new DocumentStorageUnavailableException();
        } finally {
            body.cancel();
            future.cancel(true);
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private boolean cancelled;

        BoundedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public synchronized void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (cancelled) subscription.cancel(); else subscription.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> buffers) {
            if (cancelled) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    result.completeExceptionally(new DocumentStorageUnavailableException());
                    cancel();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(new DocumentStorageUnavailableException()); }
        @Override public synchronized void onComplete() { result.complete(bytes.toByteArray()); }
        synchronized void cancel() {
            cancelled = true;
            if (subscription != null) subscription.cancel();
        }
    }
}
