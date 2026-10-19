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

/** Opens configured connections and groups repository work into one transaction. */
public final class Database {
    private static final List<String> POSTGRES_MIGRATIONS = List.of("/db/postgresql/001_initial.sql");
    /** Shared across provisioners; transaction-scoped and local to the selected database. */
    private static final long MIGRATION_LOCK = 1213418823L;
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
