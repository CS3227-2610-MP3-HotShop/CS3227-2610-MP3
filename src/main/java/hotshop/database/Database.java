package hotshop.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import hotshop.storage.SupabaseStorageConfig;

/** Opens configured connections and groups repository work into one transaction. */
public final class Database {
    private static final List<String> POSTGRES_MIGRATIONS = List.of("/db/postgresql/001_initial.sql",
            "/db/postgresql/002_image_storage.sql");
    /** Shared across provisioners; transaction-scoped and local to the selected database. */
    private static final long MIGRATION_LOCK = 1213418823L;
    private static final long IMAGE_LIFECYCLE_LOCK = 1213418824L;
    private final String url;
    private final DatabaseConfig config;
    private final List<String> migrations;

    /** Selects PostgreSQL configuration without opening a connection. */
    public Database(DatabaseConfig config) {
        this(config, POSTGRES_MIGRATIONS);
    }

    /** Stores the given configuration URL and migration resources without opening a connection. */
    Database(DatabaseConfig config, List<String> migrations) {
        url = config.url();
        this.config = config;
        this.migrations = List.copyOf(migrations);
    }

    /**
     * Checks the provisioned schema version without creating or changing any tables.
     *
     * @throws SQLException if database access fails
     */
    public void validateSchema() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
                rows.next();
                if (rows.getInt(1) != migrations.size()) {
                    throw new SQLException("Database schema is incompatible; run compatible database provisioning");
                }
            } catch (SQLException failure) {
                throw new SQLException("Database schema is unavailable or incompatible; "
                        + "run compatible database provisioning", failure.getSQLState());
            }
        }
    }

    /** Rejects inactive or mismatched storage bindings before the production runtime serves images. */
    public void validateImageStorageBindings(SupabaseStorageConfig storage) throws SQLException {
        try (Connection connection = openConnection(); var statement = connection.prepareStatement(
                "SELECT namespace, project_origin, bucket_id, active FROM image_storage_bindings");
                var rows = statement.executeQuery()) {
            java.util.Map<String, String> bindings = new java.util.HashMap<>();
            while (rows.next()) {
                if (!rows.getBoolean("active")
                        || !storage.projectUrl().toString().equals(rows.getString("project_origin"))) {
                    throw new SQLException("Private image storage is not activated; run image verification first");
                }
                bindings.put(rows.getString("namespace"), rows.getString("bucket_id"));
            }
            if (!storage.listingBucket().equals(bindings.get("listings"))
                    || !storage.profileBucket().equals(bindings.get("profiles"))) {
                throw new SQLException("Private image storage binding does not match operator configuration");
            }
        }
    }

    /** Activates both namespace buckets together after the importer verifies every current reference. */
    public static void activateImageStorageBindings(Connection connection, SupabaseStorageConfig storage, long now)
            throws SQLException {
        for (String namespace : List.of("profiles", "listings")) {
            try (var statement = connection.prepareStatement("UPDATE image_storage_bindings SET project_origin = ?, "
                    + "bucket_id = ?, active = TRUE, updated_at = ? WHERE namespace = ?")) {
                statement.setString(1, storage.projectUrl().toString());
                statement.setString(2, storage.bucket(namespace));
                statement.setLong(3, now);
                statement.setString(4, namespace);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Image storage binding is unavailable");
                }
            }
        }
    }

    /** Work may pass this connection to any participating repository. */
    @FunctionalInterface
    public interface Work<T> {
        /**
         * Uses the caller-owned connection without committing, closing, or retaining it.
         *
         * @param connection the caller-owned connection for the current database transaction
         * @return the result of the work
         * @throws SQLException if database access fails
         */
        T execute(Connection connection) throws SQLException;
    }

    /**
     * Commits successful work, rolls back failures, and closes the configured connection in either case.
     *
     * @param <T> the operation result type
     * @param work the work to execute atomically on one connection
     * @return the result produced by the committed work
     * @throws SQLException if database access fails
     */
    public <T> T executeTransaction(Work<T> work) throws SQLException {
        return executeTransaction(work, Connection.TRANSACTION_SERIALIZABLE);
    }

    /** Acquires the cross-process image lifecycle lock on the connection used by guarded transactions. */
    public ImageLifecycleGuard acquireImageLifecycleGuard() throws SQLException {
        Connection connection = openConnection();
        try {
            connection.setAutoCommit(true);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                    statement.setLong(1, IMAGE_LIFECYCLE_LOCK);
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        if (rows.getBoolean(1)) {
                            return new ImageLifecycleGuard(connection);
                        }
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Image lifecycle operation was interrupted");
                }
            }
            throw new SQLException("Image lifecycle is busy; retry the operation");
        } catch (SQLException | RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /** One database session lock and the exact connection used for its reference transactions. */
    public static final class ImageLifecycleGuard implements AutoCloseable {
        private final Connection connection;
        private boolean closed;

        private ImageLifecycleGuard(Connection connection) {
            this.connection = connection;
        }

        public <T> T transaction(Work<T> work) throws SQLException {
            if (closed || connection.isClosed()) {
                throw new SQLException("Image lifecycle connection is unavailable");
            }
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                }
            }
        }

        public Connection connection() {
            return connection;
        }

        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                    statement.setLong(1, IMAGE_LIFECYCLE_LOCK);
                    statement.execute();
                } finally {
                    connection.close();
                }
            }
        }
    }

    private <T> T executeTransaction(Work<T> work, Integer isolation) throws SQLException {
        try (Connection connection = openConnection()) {
            if (isolation != null) {
                connection.setTransactionIsolation(isolation);
            }
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, config.connectionProperties());
    }

    /**
     * Applies pending PostgreSQL migrations atomically under a transaction advisory lock.
     * Never downgrades a newer database.
     *
     * @throws SQLException if database access fails
     * @throws IOException if filesystem access or resource loading fails
     */
    public void migrate() throws SQLException, IOException {
        List<String> scripts = new ArrayList<>();
        for (String resource : migrations) {
            scripts.add(readMigration(resource));
        }
        executeTransaction(connection -> {
            try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, MIGRATION_LOCK);
                lock.execute();
            }
            int current = readSchemaVersion(connection);
            if (current > scripts.size()) {
                throw new SQLException("Database requires a newer HotShop version");
            }
            for (int version = current + 1; version <= scripts.size(); version++) {
                applyMigration(connection, version, scripts.get(version - 1));
            }
            return null;
        }, Connection.TRANSACTION_READ_COMMITTED);
    }

    private static String readMigration(String resource) throws IOException {
        try (var input = Database.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing migration " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int readSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations "
                    + "(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            try (var rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private void applyMigration(Connection connection, int version, String script) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
            insert.setInt(1, version);
            insert.setString(2, Instant.now().toString());
            insert.executeUpdate();
        }
    }
}
