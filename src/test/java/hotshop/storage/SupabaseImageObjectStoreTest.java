package hotshop.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupabaseImageObjectStoreTest {
    private HttpServer server;
    private final Map<String, byte[]> objects = new HashMap<>();
    private String apikey;
    private String contentType;
    private String method;
    private String path;
    private String upsert;
    private final AtomicBoolean slowResponseBody = new AtomicBoolean();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/storage/v1/object/", exchange -> {
            try {
                path = exchange.getRequestURI().getRawPath();
                method = exchange.getRequestMethod();
                apikey = exchange.getRequestHeaders().getFirst("apikey");
                contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                upsert = exchange.getRequestHeaders().getFirst("x-upsert");
                String key = path.substring("/storage/v1/object/".length());
                if ("POST".equals(method)) {
                    byte[] requestBody = exchange.getRequestBody().readAllBytes();
                    if (objects.containsKey(key)) {
                        send(exchange, 409, "already exists");
                    } else {
                        objects.put(key, requestBody);
                        send(exchange, 200, "created");
                    }
                } else if ("GET".equals(method) && objects.containsKey(key)) {
                    send(exchange, 200, objects.get(key));
                } else if ("DELETE".equals(method)) {
                    objects.remove(key);
                    send(exchange, 200, "deleted");
                } else {
                    send(exchange, 404, "not found");
                }
            } catch (IOException expectedWhenClientCancelsTimedOutBody) {
                // The timeout tests deliberately cancel a response while its body is being written.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void putReadDelete_privateGeneratedObject_usesFixedBucketAndServerKey() throws Exception {
        String filename = "123e4567-e89b-12d3-a456-426614174000.png";
        byte[] bytes = "png fixture bytes".getBytes(StandardCharsets.UTF_8);
        SupabaseStorageConfig config = SupabaseStorageConfig.localFixture(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
        try (SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config)) {
            storage.put("profiles", filename, bytes, "image/png");
            assertEquals("POST", method);
            assertEquals("/storage/v1/object/profileImages/" + filename, path);
            assertEquals("test-secret", apikey);
            assertEquals("image/png", contentType);
            assertEquals("false", upsert);
            assertFalse(path.contains("test-secret"));
            assertArrayEquals(bytes, storage.read("profiles", filename, 100));

            storage.delete("profiles", filename);
            assertEquals("DELETE", method);
            assertTrue(objects.isEmpty());
        }
    }

    @Test
    void read_oversizedResponse_rejectsAndClosesRequest() {
        String filename = "123e4567-e89b-12d3-a456-426614174000.jpeg";
        objects.put("profileImages/" + filename, new byte[] {1, 2, 3});
        try (SupabaseImageObjectStore storage = new SupabaseImageObjectStore(SupabaseStorageConfig.localFixture(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")))) {
            assertThrows(IOException.class, () -> storage.read("profiles", filename, 1));
        }
    }

    @Test
    void put_existingImmutableObject_returnsConflictWithoutOverwriting() throws Exception {
        String filename = "123e4567-e89b-12d3-a456-426614174000.png";
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        objects.put("profileImages/" + filename, original);
        try (SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config())) {
            assertThrows(IOException.class, () -> storage.put("profiles", filename, replacement, "image/png"));
        }
        assertArrayEquals(original, objects.get("profileImages/" + filename));
        assertEquals("POST", method);
        assertEquals("false", upsert);
    }

    @Test
    void read_requestBodyStallsAfterHeaders_obeysWholeResponseDeadline() {
        String filename = "123e4567-e89b-12d3-a456-426614174000.png";
        objects.put("profileImages/" + filename, new byte[] {1, 2, 3});
        slowResponseBody.set(true);
        try (SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(), Duration.ofMillis(150))) {
            long start = System.nanoTime();
            assertThrows(IOException.class, () -> storage.read("profiles", filename, 100));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1500);
        }
    }

    @Test
    void send_successErrorAndAbsentBodies_obeyWholeResponseDeadline() throws Exception {
        slowResponseBody.set(true);
        try (SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(), Duration.ofMillis(150))) {
            String firstName = "123e4567-e89b-12d3-a456-426614174000.png";
            assertBodyDeadline(() -> storage.put("profiles", firstName, new byte[] {1}, "image/png"));

            String missingName = "123e4567-e89b-12d3-a456-426614174001.png";
            assertBodyDeadline(() -> storage.readIfExists("profiles", missingName, 100));

            String existingName = "123e4567-e89b-12d3-a456-426614174002.png";
            objects.put("profileImages/" + existingName, new byte[] {1});
            assertBodyDeadline(() -> storage.readIfExists("profiles", existingName, 100));
            assertBodyDeadline(() -> storage.delete("profiles", existingName));

            String conflictName = "123e4567-e89b-12d3-a456-426614174003.png";
            objects.put("profileImages/" + conflictName, new byte[] {1});
            assertBodyDeadline(() -> storage.put("profiles", conflictName, new byte[] {2}, "image/png"));
        }
    }

    @Test
    void close_ownedHttpClient_terminatesClient() {
        HttpClient client = HttpClient.newHttpClient();
        SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config(), client);
        storage.close();
        assertTrue(client.isTerminated());
    }

    @Test
    void fromEnvironment_missingOrHttpSettings_rejectsWithoutEchoingValues() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> SupabaseStorageConfig.fromEnvironment(Map.of()));
        assertFalse(missing.getMessage().contains("test-secret"));
        IllegalArgumentException insecure = assertThrows(IllegalArgumentException.class,
                () -> SupabaseStorageConfig.fromEnvironment(Map.of("SUPABASE_URL", "http://localhost/",
                        "SUPABASE_SECRET_KEY", "secret-value", SupabaseStorageConfig.LISTING_BUCKET_SETTING,
                        "listingImages", SupabaseStorageConfig.PROFILE_BUCKET_SETTING, "profileImages")));
        assertFalse(insecure.getMessage().contains("secret-value"));
    }

    @Test
    void fromEnvironment_httpsOriginWithoutTrailingSlash_normalizesToProjectOrigin() {
        SupabaseStorageConfig config = SupabaseStorageConfig.fromEnvironment(Map.of(
                "SUPABASE_URL", "https://project.example",
                "SUPABASE_SECRET_KEY", "server-secret",
                SupabaseStorageConfig.LISTING_BUCKET_SETTING, "listingImages",
                SupabaseStorageConfig.PROFILE_BUCKET_SETTING, "profileImages"));

        assertEquals(URI.create("https://project.example/"), config.projectUrl());
    }

    private SupabaseStorageConfig config() {
        return SupabaseStorageConfig.localFixture(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    private void send(com.sun.net.httpserver.HttpExchange exchange, int status, String content)
            throws IOException {
        send(exchange, status, content.getBytes(StandardCharsets.UTF_8));
    }

    private void send(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] content)
            throws IOException {
        exchange.sendResponseHeaders(status, 0);
        exchange.getResponseBody().write(content, 0, Math.min(content.length, 1));
        exchange.getResponseBody().flush();
        if (slowResponseBody.get()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("fixture response interrupted");
            }
        }
        if (content.length > 1) {
            exchange.getResponseBody().write(content, 1, content.length - 1);
        }
    }

    private static void assertBodyDeadline(IoOperation operation) throws Exception {
        long start = System.nanoTime();
        assertThrows(IOException.class, operation::run);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 900);
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws IOException;
    }
}
