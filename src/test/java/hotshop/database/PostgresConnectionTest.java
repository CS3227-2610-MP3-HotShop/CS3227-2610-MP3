package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hotshop.support.PostgresFixture;

import org.junit.jupiter.api.Test;

class PostgresConnectionTest {
    @Test
    void executeTransaction_freshDatabase_usesSeparateCredentialsWithoutMigrations() throws Exception {
        try (PostgresFixture fixture = new PostgresFixture()) {
            Database database = new Database(new DatabaseConfig(
                    fixture.jdbcUrl(), fixture.username(), fixture.password()));
            int result = database.executeTransaction(connection -> {
                assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1")) {
                    assertTrue(rows.next());
                    return rows.getInt(1);
                }
            });
            assertEquals(1, result);
        }
    }
}
