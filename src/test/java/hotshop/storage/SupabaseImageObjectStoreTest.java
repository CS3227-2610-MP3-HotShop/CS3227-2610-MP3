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
import java.util.HashMap;
import java.util.Map;
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

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/storage/v1/object/", exchange -> {
            path = exchange.getRequestURI().getRawPath();
            method = exchange.getRequestMethod();
            apikey = exchange.getRequestHeaders().getFirst("apikey");
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            upsert = exchange.getRequestHeaders().getFirst("x-upsert");
            String key = path.substring("/storage/v1/object/".length());
            if ("PUT".equals(method)) {
                objects.put(key, exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(method) && objects.containsKey(key)) {
                byte[] bytes = objects.get(key);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else if ("DELETE".equals(method)) {
                objects.remove(key);
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
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
        SupabaseImageObjectStore storage = new SupabaseImageObjectStore(config);

        storage.put("profiles", filename, bytes, "image/png");
        assertEquals("PUT", method);
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

    @Test
    void read_oversizedResponse_rejectsAndClosesRequest() {
        String filename = "123e4567-e89b-12d3-a456-426614174000.jpeg";
        objects.put("profileImages/" + filename, new byte[] {1, 2, 3});
        SupabaseImageObjectStore storage = new SupabaseImageObjectStore(SupabaseStorageConfig.localFixture(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")));
        assertThrows(IOException.class, () -> storage.read("profiles", filename, 1));
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
}
