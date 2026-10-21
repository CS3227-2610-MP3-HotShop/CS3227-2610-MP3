package hotshop.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Server-mediated, private Supabase Storage REST client. */
public final class SupabaseImageObjectStore implements ImageObjectStore, AutoCloseable {
    private static final int MAX_ERROR_BYTES = 4096;
    private final SupabaseStorageConfig config;
    private final HttpClient client;

    public SupabaseImageObjectStore(SupabaseStorageConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    SupabaseImageObjectStore(SupabaseStorageConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public void put(String namespace, String filename, byte[] bytes, String contentType) throws IOException {
        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
            throw new IllegalArgumentException("Unsupported image type");
        }
        send(request(namespace, filename).header("Content-Type", contentType).header("x-upsert", "false")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), MAX_ERROR_BYTES, 200, 201);
    }

    @Override
    public byte[] read(String namespace, String filename, int maximumBytes) throws IOException {
        HttpResponse<java.io.InputStream> response = send(request(namespace, filename).GET().build(), maximumBytes,
                200);
        try (var body = response.body()) {
            byte[] result = body.readNBytes(maximumBytes + 1);
            if (result.length > maximumBytes) {
                throw new IOException("Image response exceeded its size limit");
            }
            return result;
        }
    }

    @Override
    public void delete(String namespace, String filename) throws IOException {
        send(request(namespace, filename).DELETE().build(), MAX_ERROR_BYTES, 200, 204, 404);
    }

    /** Returns null only when the provider confirms the object is absent. */
    public byte[] readIfExists(String namespace, String filename, int maximumBytes) throws IOException {
        try {
            HttpResponse<java.io.InputStream> response = client.send(request(namespace, filename).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() == 404) {
                    body.readNBytes(MAX_ERROR_BYTES);
                    return null;
                }
                if (response.statusCode() != 200) {
                    body.readNBytes(MAX_ERROR_BYTES);
                    throw new IOException("Private image storage request failed");
                }
                byte[] bytes = body.readNBytes(maximumBytes + 1);
                if (bytes.length > maximumBytes) {
                    throw new IOException("Image response exceeded its size limit");
                }
                return bytes;
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Private image storage request was interrupted");
        }
    }

    private HttpRequest.Builder request(String namespace, String filename) {
        if (filename == null || !filename.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpeg)")) {
            throw new IllegalArgumentException("Invalid managed image name");
        }
        String bucket = config.bucket(namespace);
        URI uri = config.projectUrl().resolve("storage/v1/object/" + encode(bucket) + "/" + encode(filename));
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).header("apikey", config.secretKey());
    }

    private HttpResponse<java.io.InputStream> send(HttpRequest request, int maximumBytes, int... statuses)
            throws IOException {
        try {
            HttpResponse<java.io.InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            boolean accepted = java.util.Arrays.stream(statuses).anyMatch(status -> status == response.statusCode());
            if (!accepted) {
                try (var body = response.body()) {
                    body.readNBytes(maximumBytes);
                }
                throw new IOException("Private image storage request failed");
            }
            return response;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Private image storage request was interrupted");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Override
    public void close() {
        // HttpClient owns no closeable resources in this API version.
    }
}
