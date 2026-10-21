package hotshop.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Owns a disposable PostgreSQL database; never accepts application environment configuration. */
public final class PostgresFixture implements AutoCloseable {
    public static final String IMAGE = "postgres:17.6-alpine3.22";

    private final PostgreSQLContainer container;

    public PostgresFixture() {
        container = new PostgreSQLContainer(IMAGE)
                .withDatabaseName("hotshop_test")
                .withUsername("fixture_" + UUID.randomUUID().toString().replace("-", ""))
                .withPassword(UUID.randomUUID().toString())
                .withReuse(false);
        try {
            container.start();
        } catch (RuntimeException failure) {
            container.close();
            throw new IllegalStateException("Disposable PostgreSQL setup failed; Docker must be running.", failure);
        }
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), username(), password());
    }

    public String jdbcUrl() {
        return container.getJdbcUrl();
    }

    public String username() {
        return container.getUsername();
    }

    public String password() {
        return container.getPassword();
    }

    public boolean isRunning() {
        return container.isRunning();
    }

    @Override
    public void close() {
        container.close();
    }
}
