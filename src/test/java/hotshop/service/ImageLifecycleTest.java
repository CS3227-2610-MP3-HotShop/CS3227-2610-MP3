package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hotshop.database.Database;
import hotshop.repository.ImageObjectRepository;
import hotshop.repository.UserRepository;
import hotshop.storage.ImageObjectStore;
import hotshop.storage.ImageStorage;
import hotshop.support.PostgresTestSupport;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageLifecycleTest extends PostgresTestSupport {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path directory;

    @Test
    void importImage_uploadRaceWithRecovery_serializesUploadAndRetainsTombstone() throws Exception {
        BlockingStore store = new BlockingStore(true, false);
        Database database = new Database(databaseConfig);
        ManagedImages importer = managed(database, store);
        ManagedImages recovery = managed(database, store);
        Path source = image();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var upload = executor.submit(() -> {
                try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
                    return importer.importImage(source, guard);
                }
            });
            assertTrue(store.started.await(2, TimeUnit.SECONDS));
            assertEquals("PENDING", state(database));
            var cleanup = executor.submit(() -> {
                recovery.recover();
                return true;
            });
            Thread.sleep(200);
            assertFalse(cleanup.isDone());
            assertEquals(0, store.deletes);
            store.release.countDown();
            upload.get(3, TimeUnit.SECONDS);
            assertTrue(cleanup.get(3, TimeUnit.SECONDS));
            assertEquals("PENDING", state(database));
            assertTrue(store.objects.isEmpty());
            assertEquals(1, store.deletes);
        }
    }

    @Test
    void referenceTransaction_failureAfterUpload_rollsBackReferenceAndKeepsPendingRecovery() throws Exception {
        BlockingStore store = new BlockingStore(false, false);
        Database database = new Database(databaseConfig);
        UserRepository users = new UserRepository();
        try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
            guard.transaction(connection -> {
                users.insert(connection, new hotshop.model.User("alice", "Alice", null, null),
                        new hotshop.security.PasswordHash("test", 1, new byte[] {1}, new byte[] {1}));
                return null;
            });
            String filename = managed(database, store).importImage(image(), guard);
            assertThrows(SQLException.class, () -> guard.transaction(connection -> {
                try (var statement = connection.prepareStatement("UPDATE users SET profile_image = ? WHERE id = ?")) {
                    statement.setString(1, filename);
                    statement.setString(2, "alice");
                    statement.executeUpdate();
                }
                new ImageObjectRepository().activate(connection, "profiles", filename, CLOCK.millis());
                throw new SQLException("simulated reference commit failure");
            }));
            assertNull(guard.transaction(connection -> {
                try (var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT profile_image FROM users WHERE username = 'alice'")) {
                    rows.next();
                    return rows.getString(1);
                }
            }));
        }
        ManagedImages recovery = managed(database, store);
        recovery.recover();
        assertEquals("PENDING", state(database));
        assertTrue(store.objects.isEmpty());
        assertEquals(1, store.deletes);
    }

    @Test
    void importImage_remoteWriteOutcomeUnknown_retainsReservationForLateCompletion() throws Exception {
        BlockingStore store = new BlockingStore(false, true);
        Database database = new Database(databaseConfig);
        ManagedImages images = managed(database, store);
        Path source = image();
        try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
            assertThrows(IOException.class, () -> images.importImage(source, guard));
        }
        String filename = store.objects.keySet().iterator().next().substring("profiles/".length());
        store.objects.clear();
        managed(database, store).recover();
        assertEquals("PENDING", state(database));
        assertEquals(0, store.deletes);
        store.objects.put("profiles/" + filename, Files.readAllBytes(source));
        managed(database, store).recover();
        assertEquals("PENDING", state(database));
        assertTrue(store.objects.isEmpty());
        assertEquals(1, store.deletes);
    }

    @Test
    void recover_pendingObjectReferencedByProfile_preservesObjectAndReservation() throws Exception {
        BlockingStore store = new BlockingStore(false, false);
        Database database = new Database(databaseConfig);
        UserRepository users = new UserRepository();
        Path source = image();
        byte[] bytes = Files.readAllBytes(source);
        String filename = "123e4567-e89b-12d3-a456-426614174000.png";
        try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
            guard.transaction(connection -> {
                users.insert(connection, new hotshop.model.User("alice", "Alice", filename, null),
                        new hotshop.security.PasswordHash("test", 1, new byte[] {1}, new byte[] {1}));
                try (var statement = connection.prepareStatement(
                        "UPDATE users SET profile_image = ? WHERE username = 'alice'")) {
                    statement.setString(1, filename);
                    statement.executeUpdate();
                }
                new ImageObjectRepository().reserve(connection, "profiles", filename,
                        digest(bytes), bytes.length, CLOCK.millis());
                return null;
            });
        }
        store.objects.put("profiles/" + filename, bytes);

        new ManagedImages(database, new ImageStorage(store, "profiles"), "profiles",
                ImageStorage.PROFILE_LIMITS, users::getReferencedImages, CLOCK).recover();

        assertEquals("PENDING", state(database));
        assertArrayEquals(bytes, store.objects.get("profiles/" + filename));
        assertEquals(0, store.deletes);
    }

    @Test
    void recover_pendingObjectDigestMismatch_preservesObjectAndReservation() throws Exception {
        BlockingStore store = new BlockingStore(false, false);
        Database database = new Database(databaseConfig);
        Path source = image();
        byte[] expected = Files.readAllBytes(source);
        byte[] unexpected = new byte[] {1, 2, 3};
        String filename = "123e4567-e89b-12d3-a456-426614174000.png";
        try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
            guard.transaction(connection -> {
                new ImageObjectRepository().reserve(connection, "profiles", filename,
                        digest(expected), expected.length, CLOCK.millis());
                return null;
            });
        }
        store.objects.put("profiles/" + filename, unexpected);

        managed(database, store).recover();

        assertEquals("PENDING", state(database));
        assertArrayEquals(unexpected, store.objects.get("profiles/" + filename));
        assertEquals(0, store.deletes);
    }

    private ManagedImages managed(Database database, BlockingStore store) {
        return new ManagedImages(database, new ImageStorage(store, "profiles"), "profiles",
                ImageStorage.PROFILE_LIMITS, connection -> Set.of(), CLOCK);
    }

    private static String state(Database database) throws SQLException {
        return database.executeTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT state FROM image_objects")) {
                return rows.next() ? rows.getString(1) : null;
            }
        });
    }

    private Path image() throws Exception {
        Path source = directory.resolve("source.png");
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        return source;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class BlockingStore implements ImageObjectStore {
        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final boolean block;
        private final boolean unknownOutcome;
        private volatile int deletes;

        BlockingStore(boolean block, boolean unknownOutcome) {
            this.block = block;
            this.unknownOutcome = unknownOutcome;
        }

        @Override
        public void put(String namespace, String filename, byte[] bytes, String contentType) throws IOException {
            started.countDown();
            if (block) {
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        throw new IOException("fixture upload timed out");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IOException("fixture upload interrupted");
                }
            }
            objects.put(namespace + "/" + filename, Arrays.copyOf(bytes, bytes.length));
            if (unknownOutcome) {
                throw new IOException("fixture response lost after remote write");
            }
        }

        @Override
        public byte[] read(String namespace, String filename, int maximumBytes) throws IOException {
            byte[] bytes = objects.get(namespace + "/" + filename);
            if (bytes == null) {
                throw new IOException("fixture object is absent");
            }
            return Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public byte[] readIfExists(String namespace, String filename, int maximumBytes) throws IOException {
            byte[] bytes = objects.get(namespace + "/" + filename);
            if (bytes == null) {
                return null;
            }
            if (bytes.length > maximumBytes) {
                throw new IOException("fixture image exceeds limit");
            }
            return Arrays.copyOf(bytes, bytes.length);
        }

        @Override
        public void delete(String namespace, String filename) {
            deletes++;
            objects.remove(namespace + "/" + filename);
        }
    }
}
