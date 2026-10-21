package hotshop.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import hotshop.ApplicationRuntime;
import hotshop.DescriptionRuntime;
import hotshop.database.Database;
import hotshop.database.DatabaseConfig;
import hotshop.model.ListingStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** One disposable local server per class and a distinct database for every test. */
public abstract class PostgresTestSupport {
    private static PostgresFixture server;
    protected DatabaseConfig databaseConfig;

    @BeforeAll
    static void startPostgres() {
        server = new PostgresFixture();
    }

    @AfterAll
    static void stopPostgres() {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void createTestDatabase() throws Exception {
        String name = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = server.openConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        String original = server.jdbcUrl();
        int query = original.indexOf('?');
        String suffix = query < 0 ? "" : original.substring(query);
        String base = query < 0 ? original : original.substring(0, query);
        databaseConfig = new DatabaseConfig(base.substring(0, base.lastIndexOf('/') + 1) + name + suffix,
                server.username(), server.password());
        if (provisionSchema()) {
            new Database(databaseConfig).migrate();
        }
    }

    protected boolean provisionSchema() {
        return true;
    }

    protected Connection openDatabaseConnection() throws Exception {
        return DriverManager.getConnection(databaseConfig.url(), databaseConfig.username(), databaseConfig.password());
    }

    protected ApplicationRuntime openTestRuntime(Path directory) throws Exception {
        return openTestRuntime(directory, Clock.systemDefaultZone());
    }

    protected ApplicationRuntime openTestRuntime(Path directory, Clock clock) throws Exception {
        return ApplicationRuntime.open(directory, clock, databaseConfig);
    }

    /** Uses explicit synthetic configuration for database failure tests. */
    protected ApplicationRuntime openTestRuntime(Path directory, Clock clock, DatabaseConfig config)
            throws Exception {
        return ApplicationRuntime.open(directory, clock, config);
    }

    /** Opens the test database with a caller-owned description transport and quota. */
    protected ApplicationRuntime openTestRuntime(Path directory, Clock clock, DescriptionRuntime descriptions)
            throws Exception {
        return openTestRuntime(directory, clock, databaseConfig, descriptions);
    }

    /** Opens a fixture-owned database outside the JUnit lifecycle, preserving the supplied LLM owner. */
    protected static ApplicationRuntime openTestRuntime(Path directory, Clock clock, DatabaseConfig config,
            DescriptionRuntime descriptions) throws Exception {
        return ApplicationRuntime.open(directory, clock, config, descriptions);
    }

    /** Captures public rows and sequence state so storage checks include PostgreSQL as well as local files. */
    protected String snapshotDatabase() throws Exception {
        try (var connection = openDatabaseConnection()) {
            List<String> tables = new ArrayList<>();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name")) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
            StringBuilder snapshot = new StringBuilder();
            for (String table : tables) {
                snapshot.append(table).append('\n');
                String identifier = "\"" + table.replace("\"", "\"\"") + "\"";
                try (var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT to_jsonb(row_data)::text FROM public."
                                + identifier + " row_data ORDER BY 1")) {
                    while (rows.next()) {
                        snapshot.append(rows.getString(1)).append('\n');
                    }
                }
            }
            List<String> sequences = new ArrayList<>();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT sequencename FROM pg_sequences "
                            + "WHERE schemaname = 'public' ORDER BY sequencename")) {
                while (rows.next()) {
                    sequences.add(rows.getString(1));
                }
            }
            for (String sequence : sequences) {
                snapshot.append("sequence:").append(sequence).append('\n');
                String identifier = "\"" + sequence.replace("\"", "\"\"") + "\"";
                try (var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT last_value, is_called FROM public." + identifier)) {
                    rows.next();
                    snapshot.append(rows.getLong(1)).append(':').append(rows.getBoolean(2)).append('\n');
                }
            }
            return snapshot.toString();
        }
    }

    /** Injects a deterministic PostgreSQL failure without changing the production business path. */
    protected void failWrites(String table, String event) throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE FUNCTION reject_test_write() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN RAISE EXCEPTION 'simulated failure'; END $$");
            statement.execute("CREATE TRIGGER fail_write BEFORE " + event + " ON " + table
                    + " FOR EACH ROW EXECUTE FUNCTION reject_test_write()");
        }
    }

    protected void setStatus(Path directory, UUID id, ListingStatus status) throws Exception {
        try (var connection = openDatabaseConnection();
                var statement = connection.prepareStatement("UPDATE listings SET status = ? WHERE id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, id.toString());
            if (statement.executeUpdate() != 1) {
                throw new AssertionError("Expected one listing update");
            }
        }
    }
}
