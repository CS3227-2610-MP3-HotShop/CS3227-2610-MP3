package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import hotshop.support.PostgresFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostgresDatabaseTest {
    private static final String FIRST = "/db/test-migration/001_first.sql";
    private static final String SECOND = "/db/test-migration/002_second.sql";

    @Test
    void migrate_freshAndRepeatedProvisioning_preservesRecords() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture), List.of(FIRST, SECOND));
            database.migrate();
            execute(database, "INSERT INTO first VALUES (42)");
            database.migrate();
            assertEquals(42, scalar(fixture, "SELECT value FROM first"));
            assertEquals(2, scalar(fixture, "SELECT COUNT(*) FROM schema_migrations"));
            database.validateSchema();
        }
    }

    @Test
    void migrate_failedPendingBatch_rollsBackAllPendingVersions() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture),
                    List.of(FIRST, "/db/test-migration/002_broken.sql"));
            SQLException failure = assertThrows(SQLException.class, database::migrate);
            assertEquals("42P01", failure.getSQLState());
            assertFalse(tableExists(fixture, "first"));
            assertFalse(tableExists(fixture, "partial"));
            if (tableExists(fixture, "schema_migrations")) {
                assertEquals(0, scalar(fixture, "SELECT COUNT(*) FROM schema_migrations"));
            }
        }
    }

    @Test
    void migrate_concurrentProvisioners_applyEachVersionOnce() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture();
                var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                new Database(config(fixture), List.of(FIRST, SECOND)).migrate();
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                new Database(config(fixture), List.of(FIRST, SECOND)).migrate();
                return null;
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            assertEquals(2, scalar(fixture, "SELECT COUNT(*) FROM schema_migrations"));
            assertTrue(tableExists(fixture, "first"));
            assertTrue(tableExists(fixture, "second"));
        }
    }

    @Test
    void validateSchema_missingSchema_rejectsWithoutCreatingTables() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture));
            assertThrows(SQLException.class, database::validateSchema);
            assertEquals(0, scalar(fixture, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'public'"));
        }
    }

    @Test
    void validateSchema_olderAndNewerVersions_rejectsWithoutModification() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            new Database(config(fixture), List.of(FIRST)).migrate();
            Database newer = new Database(config(fixture), List.of(FIRST, SECOND));
            assertThrows(SQLException.class, newer::validateSchema);
            assertFalse(tableExists(fixture, "second"));
            newer.migrate();
            Database older = new Database(config(fixture), List.of(FIRST));
            assertThrows(SQLException.class, older::validateSchema);
            assertThrows(SQLException.class, older::migrate);
            assertEquals(2, scalar(fixture, "SELECT COUNT(*) FROM schema_migrations"));
        }
    }

    @Test
    void executeTransaction_sqlFailure_rollsBackAndClosesConnection() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture), List.of(FIRST));
            database.migrate();
            Connection[] observed = new Connection[1];
            assertThrows(SQLException.class, () -> database.executeTransaction(connection -> {
                observed[0] = connection;
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO first VALUES (7)");
                }
                throw new SQLException("synthetic failure");
            }));
            assertTrue(observed[0].isClosed());
            assertEquals(0, scalar(fixture, "SELECT COUNT(*) FROM first"));
        }
    }

    @Test
    void executeTransaction_success_usesSerializableAndCommits() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture), List.of(FIRST));
            database.migrate();
            database.executeTransaction(connection -> {
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO first VALUES (9)");
                }
                return null;
            });
            assertEquals(9, scalar(fixture, "SELECT value FROM first"));
        }
    }

    @Test
    void migrate_releasedSchema_roundTripsBinaryAndNullableValues() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture));
            database.migrate();
            byte[] binary = {0, -1, 127, -128};
            database.executeTransaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO users(id,username,normalized_username,display_name) "
                            + "VALUES ('u','Alice','alice','Alice')");
                }
                try (var statement = connection.prepareStatement(
                        "INSERT INTO credentials VALUES ('u','PBKDF2',1,?,?)")) {
                    statement.setBytes(1, binary);
                    statement.setBytes(2, binary);
                    statement.executeUpdate();
                }
                return null;
            });
            try (var connection = fixture.openConnection();
                    var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT salt,password_hash,profile_image "
                            + "FROM credentials JOIN users ON user_id=id")) {
                assertTrue(rows.next());
                assertArrayEquals(binary, rows.getBytes(1));
                assertArrayEquals(binary, rows.getBytes(2));
                assertNull(rows.getString(3));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483648L, 9223372036854775807L})
    void migrate_releasedSchema_preservesLongTimestampsAndCounters(long value) throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture));
            database.migrate();
            seedMarketplace(database);
            database.executeTransaction(connection -> {
                try (var statement = connection.prepareStatement("INSERT INTO conversations "
                        + "(id,listing_id,buyer_id,seller_id,created_at,buyer_read_sequence) "
                        + "VALUES ('c','l','b','u',?,?)")) {
                    statement.setLong(1, value);
                    statement.setLong(2, value);
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("INSERT INTO messages "
                        + "VALUES ('m','c','b',?,'Hello',?)")) {
                    statement.setLong(1, value);
                    statement.setLong(2, value);
                    statement.executeUpdate();
                }
                return null;
            });
            assertEquals(value, scalar(fixture, "SELECT created_at FROM conversations"));
            assertEquals(value, scalar(fixture, "SELECT buyer_read_sequence FROM conversations"));
            assertEquals(value, scalar(fixture, "SELECT sequence FROM messages"));
            assertEquals(value, scalar(fixture, "SELECT sent_at FROM messages"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO users(id,username,normalized_username,display_name) VALUES ('x','Other','alice','X')",
            "INSERT INTO credentials VALUES ('missing','PBKDF2',1,decode('00','hex'),decode('00','hex'))",
            "UPDATE listings SET price_cents=0 WHERE id='l'",
            "UPDATE listings SET price_cents=100000001 WHERE id='l'",
            "UPDATE listings SET updated_at=-1 WHERE id='l'",
            "UPDATE users SET display_name=NULL WHERE id='u'"})
    void migrate_releasedSchema_rejectsInvalidPersistentState(String invalidSql) throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture));
            database.migrate();
            seedMarketplace(database);
            assertThrows(SQLException.class, () -> execute(database, invalidSql));
            assertEquals(2, scalar(fixture, "SELECT COUNT(*) FROM users"));
        }
    }

    @Test
    void migrate_releasedSchema_enforcesPendingOfferPartialUniqueness() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture));
            database.migrate();
            seedMarketplace(database);
            execute(database, "INSERT INTO offers VALUES ('old','l','b',100,'WITHDRAWN',0,1)");
            execute(database, "INSERT INTO offers VALUES ('one','l','b',100,'PENDING',2,NULL)");
            assertThrows(SQLException.class, () -> execute(database,
                    "INSERT INTO offers VALUES ('two','l','b',100,'PENDING',3,NULL)"));
            assertEquals(2, scalar(fixture, "SELECT COUNT(*) FROM offers"));
        }
    }

    @Test
    void executeTransaction_runtimeFailure_rollsBackAllWork() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(config(fixture), List.of(FIRST));
            database.migrate();
            assertThrows(IllegalStateException.class, () -> database.executeTransaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO first VALUES (7)");
                }
                throw new IllegalStateException("synthetic failure");
            }));
            assertEquals(0, scalar(fixture, "SELECT COUNT(*) FROM first"));
        }
    }

    private static void seedMarketplace(Database database) throws SQLException {
        execute(database, "INSERT INTO users(id,username,normalized_username,display_name) "
                + "VALUES ('u','Alice','alice','Alice'),('b','Bob','bob','Bob')");
        execute(database, "INSERT INTO listings (id,seller_id,title,description,category,price_cents,"
                + "condition,pickup_location,status,created_at,updated_at) "
                + "VALUES ('l','u','Title','Description','OTHER',100,'NEW','Campus','AVAILABLE',0,0)");
    }

    private static DatabaseConfig config(PostgresFixture fixture) {
        return new DatabaseConfig(fixture.jdbcUrl(), fixture.username(), fixture.password());
    }

    private static void execute(Database database, String sql) throws SQLException {
        database.executeTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    private static long scalar(PostgresFixture fixture, String sql) throws SQLException {
        try (var connection = fixture.openConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static boolean tableExists(PostgresFixture fixture, String name) throws SQLException {
        try (var connection = fixture.openConnection();
                var statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, "public." + name);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1) != null;
            }
        }
    }
}
