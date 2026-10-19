package hotshop.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class PostgresFixtureTest {
    @Test
    void openConnection_twoFreshFixtures_isolatesRecords() throws Exception {
        try (PostgresFixture first = new PostgresFixture();
                PostgresFixture second = new PostgresFixture();
                Connection firstConnection = first.openConnection();
                Connection secondConnection = second.openConnection();
                Statement firstStatement = firstConnection.createStatement();
                Statement secondStatement = secondConnection.createStatement()) {
            assertEquals("PostgreSQL", firstConnection.getMetaData().getDatabaseProductName());
            assertEquals(17, firstConnection.getMetaData().getDatabaseMajorVersion());
            firstStatement.execute("CREATE TABLE fixture_record (id INTEGER PRIMARY KEY)");
            firstStatement.executeUpdate("INSERT INTO fixture_record VALUES (1)");
            try (ResultSet records = firstStatement.executeQuery("SELECT COUNT(*) FROM fixture_record")) {
                assertTrue(records.next());
                assertEquals(1, records.getInt(1));
            }
            try (ResultSet tables = secondStatement.executeQuery("SELECT to_regclass('public.fixture_record')")) {
                assertTrue(tables.next());
                assertNull(tables.getString(1));
            }
        }
    }

    @Test
    void close_runningFixture_stopsContainer() {
        PostgresFixture fixture = new PostgresFixture();
        try (fixture) {
            assertTrue(fixture.isRunning());
        }
        assertFalse(fixture.isRunning());
    }
}
