package hotshop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import hotshop.database.Database;
import hotshop.repository.ImageCleanupRepository;
import hotshop.repository.ImageObjectRepository;
import hotshop.storage.ImageStorage;

/**
 * One feature's managed image namespace: imports files, queues retired files, and recovers
 * orphans. Coordinates the non-atomic filesystem/database boundary on the service worker.
 */
final class ManagedImages {
    private static final int BYTES_PER_MIB = 1024 * 1024;
    private static final int RECOVERY_BATCH_SIZE = 16;
    private static final System.Logger LOGGER = System.getLogger(ManagedImages.class.getName());
    private final Database database;
    private final ImageStorage storage;
    private final String namespace;
    private final ImageStorage.Limits limits;
    private final References references;
    private final Clock clock;
    private final ImageCleanupRepository cleanup = new ImageCleanupRepository();
    private final ImageObjectRepository objects = new ImageObjectRepository();
    private final Set<String> confirmedUploads = ConcurrentHashMap.newKeySet();
    private String pendingRecoveryCursor;
    private String cleanupRecoveryCursor;

    /** Lists every filename in this namespace that saved records still reference. */
    @FunctionalInterface
    interface References {
        /**
         * Reads the live image references using the caller's transaction.
         *
         * @param connection the caller-owned database connection
         * @return the filenames still referenced by persisted records
         * @throws SQLException if the reference query fails
         */
        Set<String> find(Connection connection) throws SQLException;
    }

    ManagedImages(Database database, ImageStorage storage, String namespace, ImageStorage.Limits limits,
            References references, Clock clock) {
        this.database = database;
        this.storage = storage;
        this.namespace = namespace;
        this.limits = limits;
        this.references = references;
        this.clock = clock;
    }

    /**
     * Copies a validated image into this namespace; invalid images are reported as validation
     * failures that restate this namespace's limits.
     */
    String importImage(Path source, Database.ImageLifecycleGuard guard) throws IOException {
        byte[] bytes;
        try {
            bytes = ImageStorage.readValidatedBytes(source, limits);
        } catch (IllegalArgumentException exception) {
            throw ServiceException.validation(exception.getMessage() + ". Images must be JPEG or PNG, at most "
                    + limits.maximumBytes() / BYTES_PER_MIB + " MiB and " + limits.maximumWidth() + " by "
                    + limits.maximumHeight() + " pixels.");
        }
        String name = java.util.UUID.randomUUID() + "." + ImageStorage.detectFormat(bytes);
        try {
            long now = clock.millis();
            String digest = digest(bytes);
            guard.transaction(connection -> {
                objects.reserve(connection, namespace, name, digest, bytes.length, now);
                return null;
            });
            storage.put(name, bytes, "image/" + ImageStorage.detectFormat(bytes));
            confirmedUploads.add(name);
        } catch (SQLException failure) {
            throw new IOException("Image storage is unavailable", failure);
        }
        return name;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    byte[] read(String filename) throws IOException {
        byte[] bytes = storage.read(filename, limits.maximumBytes());
        try {
            ImageStorage.validateBytes(bytes, limits);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Stored image is unavailable", failure);
        }
        return bytes;
    }

    void activate(Connection connection, String filename, long now) throws SQLException {
        objects.activate(connection, namespace, filename, now);
    }

    /** Queues a retired file in the caller's transaction, so it is removed only after commit. */
    void schedule(Connection connection, String filename) throws SQLException {
        cleanup.schedule(connection, namespace, filename);
        objects.retire(connection, namespace, filename, clock.millis());
    }

    /** Discovers crash-created orphans in this namespace and retries durable cleanup. */
    void recover() throws SQLException, IOException {
        try (Database.ImageLifecycleGuard guard = database.acquireImageLifecycleGuard()) {
            Set<String> referenced = guard.transaction(references::find);
            guard.transaction(connection -> {
                for (String name : confirmedUploads) {
                    if (!referenced.contains(name)) {
                        objects.retire(connection, namespace, name, clock.millis());
                        cleanup.schedule(connection, namespace, name);
                    }
                }
                return null;
            });
            List<ImageObjectRepository.PendingObject> pendingObjects = guard.transaction(connection -> {
                List<ImageObjectRepository.PendingObject> batch = objects.getPending(
                        connection, namespace, pendingRecoveryCursor, RECOVERY_BATCH_SIZE);
                if (batch.isEmpty() && pendingRecoveryCursor != null) {
                    pendingRecoveryCursor = null;
                    batch = objects.getPending(connection, namespace, null, RECOVERY_BATCH_SIZE);
                }
                return batch;
            });
            Set<String> verifiedPending = new HashSet<>();
            for (ImageObjectRepository.PendingObject pending : pendingObjects) {
                pendingRecoveryCursor = pending.filename();
                if (referenced.contains(pending.filename())) {
                    continue;
                }
                try {
                    byte[] observed = storage.readIfExists(pending.filename(), limits.maximumBytes());
                    if (observed != null && digest(observed).equals(pending.sha256())) {
                        verifiedPending.add(pending.filename());
                        guard.transaction(connection -> {
                            cleanup.schedule(connection, namespace, pending.filename());
                            return null;
                        });
                    } else if (observed != null) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Image recovery found a conflicting object in " + namespace);
                    }
                } catch (IOException | IllegalArgumentException failure) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Image recovery in " + namespace + " will retry later");
                }
            }
            Set<String> files = storage.listManagedFiles();
            if (!files.isEmpty()) {
                guard.transaction(connection -> {
                    Set<String> currentReferences = references.find(connection);
                    for (String name : files) {
                        if (!currentReferences.contains(name)) {
                            cleanup.schedule(connection, namespace, name);
                        }
                    }
                    return null;
                });
            }
            List<String> pendingCleanup = guard.transaction(connection -> {
                List<String> batch = cleanup.getPendingBatch(
                        connection, namespace, cleanupRecoveryCursor, RECOVERY_BATCH_SIZE);
                if (batch.isEmpty() && cleanupRecoveryCursor != null) {
                    cleanupRecoveryCursor = null;
                    batch = cleanup.getPendingBatch(connection, namespace, null, RECOVERY_BATCH_SIZE);
                }
                return batch;
            });
            for (String name : pendingCleanup) {
                cleanupRecoveryCursor = name;
                try {
                    boolean delete = guard.transaction(connection -> {
                        if (references.find(connection).contains(name)) {
                            return false;
                        }
                        return objects.isRetired(connection, namespace, name)
                                || (objects.isPending(connection, namespace, name)
                                        && verifiedPending.contains(name)) || !storage.isRemote();
                    });
                    if (delete) {
                        storage.delete(name);
                        guard.transaction(connection -> {
                            if (!objects.isPending(connection, namespace, name)) {
                                objects.finish(connection, namespace, name);
                            }
                            cleanup.finish(connection, namespace, name);
                            return null;
                        });
                        confirmedUploads.remove(name);
                    }
                } catch (SQLException | IOException | IllegalArgumentException exception) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Image cleanup in " + namespace + " will retry later");
                }
            }
        }
    }

    /**
     * Recovers after an operation; failures are logged and retried at startup. Rechecking
     * references matters even if a commit succeeded but closing its connection failed.
     */
    void recoverSafely() {
        try {
            recover();
        } catch (SQLException | IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Image cleanup in " + namespace + " will retry at startup");
        }
    }
}
