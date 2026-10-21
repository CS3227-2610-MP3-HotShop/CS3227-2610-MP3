package hotshop.database;

import java.io.IOException;
import java.sql.SQLException;

/** Standalone operator command for creating or upgrading the configured database schema. */
public final class DatabaseProvisioning {
    private DatabaseProvisioning() {
    }

    /**
     * Provisions using environment configuration without starting the application.
     *
     * @param args unused command arguments
     */
    public static void main(String[] args) {
        try {
            DatabaseConfig config = DatabaseConfig.fromEnvironment(System.getenv());
            new Database(config).migrate();
        } catch (IOException | SQLException | RuntimeException failure) {
            System.err.println("Database provisioning failed");
            System.exit(1);
        }
    }
}
