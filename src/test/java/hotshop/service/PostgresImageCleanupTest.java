package hotshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import hotshop.database.Database;
import hotshop.repository.ImageCleanupRepository;
import hotshop.support.PostgresTestSupport;

import org.junit.jupiter.api.Test;

class PostgresImageCleanupTest extends PostgresTestSupport {
    @Test
    void schedule_duplicateFilename_keepsOneRequestPerNamespace() throws Exception {
        Database database = new Database(databaseConfig);
        ImageCleanupRepository cleanup = new ImageCleanupRepository();
        database.executeTransaction(connection -> {
            cleanup.schedule(connection, "profiles", "image.png");
            cleanup.schedule(connection, "profiles", "image.png");
            cleanup.schedule(connection, "listings", "image.png");
            assertEquals(Set.of("image.png"), cleanup.getPending(connection, "profiles"));
            assertEquals(Set.of("image.png"), cleanup.getPending(connection, "listings"));
            return null;
        });
        database.executeTransaction(connection -> {
            cleanup.finish(connection, "profiles", "image.png");
            assertEquals(Set.of(), cleanup.getPending(connection, "profiles"));
            assertEquals(Set.of("image.png"), cleanup.getPending(connection, "listings"));
            return null;
        });
    }
}
