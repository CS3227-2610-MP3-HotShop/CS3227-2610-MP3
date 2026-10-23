package hotshop.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import hotshop.database.Database;
import hotshop.support.PostgresTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageImportCommandTest extends PostgresTestSupport {
    private static final String PROFILE_NAME = "123e4567-e89b-12d3-a456-426614174000.png";
    private static final String LISTING_NAME = "123e4567-e89b-12d3-a456-426614174001.png";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    private final Map<String, byte[]> remoteObjects = new HashMap<>();
    private HttpServer server;
    private Path windowsRoot;
    private Path dockerRoot;
    private byte[] profileBytes;
    private byte[] listingBytes;
    private int putCount;

    @BeforeEach
    void startStorageFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/storage/v1/object/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            String key = path.substring("/storage/v1/object/".length());
            if ("POST".equals(exchange.getRequestMethod())) {
                putCount++;
                remoteObjects.put(key, exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(exchange.getRequestMethod()) && remoteObjects.containsKey(key)) {
                byte[] bytes = remoteObjects.get(key);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
        windowsRoot = Files.createDirectories(directory.resolve("windows/images"));
        dockerRoot = Files.createDirectories(directory.resolve("docker/images"));
        profileBytes = png(0xff224466);
        listingBytes = png(0xff663322);
        seedReferencedRecords();
        writeBoth("profiles", PROFILE_NAME, profileBytes);
        writeBoth("listings", LISTING_NAME, listingBytes);
    }

    @AfterEach
    void stopStorageFixture() {
        server.stop(0);
    }

    @Test
    void migrateImages_identicalSourceCopies_importsEachReferenceOnceAndActivatesBothBindings() throws Exception {
        ImageImportCommand.runWith(arguments(false), new Database(databaseConfig), storageConfig(), storage(), CLOCK);

        assertEquals(2, putCount);
        assertArrayEquals(profileBytes, remoteObjects.get("profileImages/" + PROFILE_NAME));
        assertArrayEquals(listingBytes, remoteObjects.get("listingImages/" + LISTING_NAME));
        assertArrayEquals(profileBytes, Files.readAllBytes(windowsRoot.resolve("profiles").resolve(PROFILE_NAME)));
        assertArrayEquals(profileBytes, Files.readAllBytes(dockerRoot.resolve("profiles").resolve(PROFILE_NAME)));
        try (Connection connection = openDatabaseConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM image_storage_bindings WHERE active")) {
            rows.next();
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void migrateImages_conflictingSourceCopies_stopsBeforeRemoteWritesOrActivation() throws Exception {
        Files.write(dockerRoot.resolve("profiles").resolve(PROFILE_NAME), png(0xff000000));

        assertThrows(java.io.IOException.class, () -> ImageImportCommand.runWith(arguments(false),
                new Database(databaseConfig), storageConfig(), storage(), CLOCK));

        assertEquals(0, putCount);
        assertTrue(remoteObjects.isEmpty());
        assertBindingsInactive();
    }

    @Test
    void migrateImages_missingSourceOrRemoteVerificationFailure_preservesBindingState() throws Exception {
        Files.delete(dockerRoot.resolve("listings").resolve(LISTING_NAME));
        Files.delete(windowsRoot.resolve("listings").resolve(LISTING_NAME));
        assertThrows(java.io.IOException.class, () -> ImageImportCommand.runWith(arguments(true),
                new Database(databaseConfig), storageConfig(), storage(), CLOCK));
        assertEquals(0, putCount);
        assertBindingsInactive();
    }

    @Test
    void migrateImages_remoteConflict_neverOverwritesAndDoesNotActivateEitherNamespace() throws Exception {
        remoteObjects.put("profileImages/" + PROFILE_NAME, png(0xffeeeeee));

        assertThrows(java.io.IOException.class, () -> ImageImportCommand.runWith(arguments(false),
                new Database(databaseConfig), storageConfig(), storage(), CLOCK));

        assertArrayEquals(png(0xffeeeeee), remoteObjects.get("profileImages/" + PROFILE_NAME));
        assertBindingsInactive();
    }

    private void seedReferencedRecords() throws Exception {
        try (Connection connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users(id, username, normalized_username, display_name, profile_image) "
                    + "VALUES ('owner', 'owner', 'owner', 'Owner', '" + PROFILE_NAME + "')");
            statement.executeUpdate("INSERT INTO listings(id, seller_id, title, description, category, price_cents, "
                    + "condition, pickup_location, status, created_at, updated_at) VALUES ('listing', 'owner', 'Item', "
                    + "'Description', 'OTHER', 100, 'GOOD', 'Place', 'AVAILABLE', 1, 1)");
            statement.executeUpdate("INSERT INTO listing_images(listing_id, display_order, filename) VALUES "
                    + "('listing', 0, '" + LISTING_NAME + "')");
        }
    }

    private void writeBoth(String namespace, String name, byte[] bytes) throws Exception {
        Path windows = Files.createDirectories(windowsRoot.resolve(namespace)).resolve(name);
        Path docker = Files.createDirectories(dockerRoot.resolve(namespace)).resolve(name);
        Files.write(windows, bytes);
        Files.write(docker, bytes);
    }

    private String[] arguments(boolean verifyOnly) {
        return verifyOnly
                ? new String[] {"--windows-root", windowsRoot.toString(), "--docker-root", dockerRoot.toString(),
                        "--verify-only"}
                : new String[] {"--windows-root", windowsRoot.toString(), "--docker-root", dockerRoot.toString()};
    }

    private SupabaseStorageConfig storageConfig() {
        return SupabaseStorageConfig.localFixture(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/"));
    }

    private SupabaseImageObjectStore storage() {
        return new SupabaseImageObjectStore(storageConfig());
    }

    private void assertBindingsInactive() throws Exception {
        try (Connection connection = openDatabaseConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM image_storage_bindings WHERE active")) {
            rows.next();
            assertFalse(rows.getInt(1) > 0);
        }
    }

    private static byte[] png(int rgb) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
