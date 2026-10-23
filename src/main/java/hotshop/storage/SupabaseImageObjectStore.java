package hotshop.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Server-mediated, private Supabase Storage REST client. */
public final class SupabaseImageObjectStore implements ImageObjectStore, AutoCloseable {
    private static final int MAX_ERROR_BYTES = 4096;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private final SupabaseStorageConfig config;
    private final HttpClient client;
    private final Duration requestTimeout;

    public SupabaseImageObjectStore(SupabaseStorageConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), REQUEST_TIMEOUT);
    }

    SupabaseImageObjectStore(SupabaseStorageConfig config, HttpClient client) {
        this(config, client, REQUEST_TIMEOUT);
    }

    SupabaseImageObjectStore(SupabaseStorageConfig config, HttpClient client, Duration requestTimeout) {
        this.config = config;
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void put(String namespace, String filename, byte[] bytes, String contentType) throws IOException {
        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
            throw new IllegalArgumentException("Unsupported image type");
        }
        HttpResponse<byte[]> response = send(request(namespace, filename).header("Content-Type", contentType)
                .header("x-upsert", "false").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                MAX_ERROR_BYTES, 200, 201);
        acceptStatus(response.statusCode(), 200, 201);
    }

    @Override
    public byte[] read(String namespace, String filename, int maximumBytes) throws IOException {
        HttpResponse<byte[]> response = send(request(namespace, filename).GET().build(), maximumBytes, 200);
        acceptStatus(response.statusCode(), 200);
        return response.body();
    }

    @Override
    public void delete(String namespace, String filename) throws IOException {
        HttpResponse<byte[]> response = send(request(namespace, filename).DELETE().build(), MAX_ERROR_BYTES,
                200, 204, 404);
        acceptStatus(response.statusCode(), 200, 204, 404);
    }

    /** Returns null only when the provider confirms the object is absent. */
    @Override
    public byte[] readIfExists(String namespace, String filename, int maximumBytes) throws IOException {
        HttpResponse<byte[]> response = send(request(namespace, filename).GET().build(), maximumBytes, 200, 404);
        if (response.statusCode() == 404) {
            return null;
        }
        acceptStatus(response.statusCode(), 200);
        return response.body();
    }

    private HttpRequest.Builder request(String namespace, String filename) {
        if (filename == null || !filename.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpeg)")) {
            throw new IllegalArgumentException("Invalid managed image name");
        }
        String bucket = config.bucket(namespace);
        URI uri = config.projectUrl().resolve("storage/v1/object/" + encode(bucket) + "/" + encode(filename));
        return HttpRequest.newBuilder(uri).timeout(requestTimeout).header("apikey", config.secretKey());
    }

    private HttpResponse<byte[]> send(HttpRequest request, int maximumBytes, int... statuses) throws IOException {
        CompletableFuture<HttpResponse<byte[]>> responseFuture = client.sendAsync(request, response -> {
            int limit = response.statusCode() != 404
                    && Arrays.stream(statuses).anyMatch(status -> status == response.statusCode())
                    ? maximumBytes : MAX_ERROR_BYTES;
            return new BoundedBodySubscriber(limit);
        });
        try {
            return responseFuture.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            responseFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Private image storage request was interrupted");
        } catch (TimeoutException failure) {
            responseFuture.cancel(true);
            throw new IOException("Private image storage request timed out");
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof CompletionException completion && completion.getCause() != null) {
                cause = completion.getCause();
            }
            if (cause instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Private image storage request failed");
        }
    }

    private static void acceptStatus(int status, int... expected) throws IOException {
        if (Arrays.stream(expected).noneMatch(code -> code == status)) {
            throw new IOException("Private image storage request failed");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public void close() {
        client.close();
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximumBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        BoundedBodySubscriber(int maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int count = buffer.remaining();
                if ((long) bytes.size() + count > maximumBytes) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Image response exceeded its size limit"));
                    return;
                }
                byte[] chunk = new byte[count];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
