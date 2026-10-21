package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import hotshop.ApplicationRuntime;
import hotshop.database.Database;
import hotshop.support.PostgresTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostgresServiceErrorsTest extends PostgresTestSupport {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"40001", "40P01"})
    void transaction_conflict_returnsSafeRetryWithoutReplayingCallback(String state) {
        AtomicInteger calls = new AtomicInteger();
        ServiceException failure = assertThrows(ServiceException.class,
                () -> ServiceSupport.transaction(new Database(databaseConfig), "Storage unavailable", connection -> {
                    calls.incrementAndGet();
                    throw new SQLException("synthetic sensitive driver detail", state);
                }));
        assertEquals(1, calls.get());
        assertEquals(ServiceException.Code.STORAGE, failure.getCode());
        assertTrue(failure.getMessage().contains("retry"));
        assertNull(failure.getCause());
    }

    @Test
    void register_usernameConstraintRace_reportsUsernameUnavailableAndRollsBack() throws Exception {
        try (ApplicationRuntime runtime = openTestRuntime(directory)) {
            installConflict("users", "INSERT INTO users(id,username,normalized_username,display_name) "
                    + "VALUES ('conflicting-id',NEW.username,NEW.normalized_username,NEW.display_name)");
            ServiceException failure = failure(() -> runtime.getAccounts().register("alice", "Sample1!", "Alice")
                    .join());
            assertEquals(ServiceException.Code.USERNAME_UNAVAILABLE, failure.getCode());
            assertNull(failure.getCause());
            assertNoRows("users");
        }
    }

    @Test
    void register_unrelatedUniqueConstraint_reportsStorageAndRollsBack() throws Exception {
        try (ApplicationRuntime runtime = openTestRuntime(directory)) {
            installConflict("credentials", "INSERT INTO credentials VALUES "
                    + "(NEW.user_id,NEW.algorithm,NEW.iterations,NEW.salt,NEW.password_hash)");
            ServiceException failure = failure(() -> runtime.getAccounts().register("alice", "Sample1!", "Alice")
                    .join());
            assertEquals(ServiceException.Code.STORAGE, failure.getCode());
            assertNull(failure.getCause());
            assertNoRows("users");
            assertNoRows("credentials");
        }
    }

    private ServiceException failure(Runnable operation) {
        CompletionException wrapped = assertThrows(CompletionException.class, operation::run);
        assertTrue(wrapped.getCause() instanceof ServiceException);
        return (ServiceException) wrapped.getCause();
    }

    private void installConflict(String table, String insert) throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE FUNCTION conflict_test_write() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN IF pg_trigger_depth()=1 THEN " + insert + "; END IF; RETURN NEW; END $$");
            statement.execute("CREATE TRIGGER conflict_write BEFORE INSERT ON " + table
                    + " FOR EACH ROW EXECUTE FUNCTION conflict_test_write()");
        }
    }

    private void assertNoRows(String table) throws Exception {
        try (var connection = openDatabaseConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }
}
