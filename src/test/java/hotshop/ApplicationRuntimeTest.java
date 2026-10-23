package hotshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import hotshop.database.Database;
import hotshop.database.DatabaseConfig;
import hotshop.support.PostgresTestSupport;
import java.sql.SQLException;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.service.ServiceException;

class ApplicationRuntimeTest extends PostgresTestSupport {
    @TempDir
    Path directory;

    @Test
    void open_missingSchema_preservesLegacyFileAndReleasesLock() throws Exception {
        Path file = directory.resolve("marketplace.db");
        Files.writeString(file, "not a database");
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE schema_migrations");
        }
        SQLException failure = assertThrows(SQLException.class, () -> openTestRuntime(directory));
        assertEquals("not a database", Files.readString(file));
        org.junit.jupiter.api.Assertions.assertFalse(failure.toString().contains(databaseConfig.username()));
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            statement.execute("INSERT INTO schema_migrations VALUES (1, 'provisioned'), (2, 'provisioned')");
        }
        try (ApplicationRuntime runtime = openTestRuntime(directory)) {
            assertNotNull(runtime.getAccounts());
        }
    }

    @Test
    void open_newerSchema_rejectsDowngrade() throws Exception {
        try (ApplicationRuntime runtime = openTestRuntime(directory)) {
            assertNotNull(runtime.getAccounts());
        }
        try (var connection = openDatabaseConnection();
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO schema_migrations VALUES (999, 'future')");
        }
        assertThrows(SQLException.class, () -> openTestRuntime(directory));
    }

    @Test
    void close_queuedRegistration_drainsWorkAndRejectsNewOperations() throws Exception {
        ApplicationRuntime runtime = openTestRuntime(directory);
        var pending = runtime.getAccounts().register("alice", "Sample1!", "Alice");
        runtime.close();
        runtime.close();
        var id = pending.join().getId();
        var failure = assertThrows(CompletionException.class, () -> runtime.getAccounts().getCurrentUserId().join());
        assertEquals(ServiceException.Code.SESSION, ((ServiceException) failure.getCause()).getCode());
        try (ApplicationRuntime reopened = openTestRuntime(directory)) {
            assertEquals(id, reopened.getAccounts().login("alice", "Sample1!").join().getId());
        }
    }

    @Test
    void open_lockedDirectory_rejectsSecondInstanceAndAllowsReopen() throws Exception {
        try (ApplicationRuntime first = openTestRuntime(directory)) {
            assertNotNull(first);
            assertThrows(IOException.class, () -> openTestRuntime(directory));
        }
        try (ApplicationRuntime reopened = openTestRuntime(directory)) {
            assertNotNull(reopened);
        }
    }
    @Test
    void open_unreachableDatabase_redactsFailureAndReleasesLock() throws Exception {
        DatabaseConfig unavailable = new DatabaseConfig("jdbc:postgresql://127.0.0.1:1/unreachable",
                "synthetic_user", "synthetic_password");
        SQLException failure = assertThrows(SQLException.class,
                () -> openTestRuntime(directory, Clock.systemUTC(), unavailable));
        org.junit.jupiter.api.Assertions.assertFalse(failure.toString().contains("synthetic_user"));
        org.junit.jupiter.api.Assertions.assertNull(failure.getCause());
        try (ApplicationRuntime runtime = openTestRuntime(directory)) {
            assertNotNull(runtime.getAccounts());
        }
    }

    @Test
    void open_readOnlyDatabase_doesNotProvisionOrMutateHistory() throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE " + connection.getCatalog() + " SET default_transaction_read_only = on");
        }
        try (ApplicationRuntime runtime = openTestRuntime(directory)) {
            assertNotNull(runtime.getAccounts());
        }
        new Database(databaseConfig).validateSchema();
    }

}
