package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import hotshop.support.PostgresFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseProvisioningCommandTest {
    @TempDir
    Path directory;

    @Test
    void migrateDatabase_freshAndRepeatedCommand_preservesRecords() throws Exception {
        copyBuildContext();
        try (PostgresFixture fixture = new PostgresFixture()) {
            CommandResult first = provision(fixture, fixture.password());
            assertEquals(0, first.exitCode(), "Actual migrateDatabase command must succeed");
            try (var connection = fixture.openConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(id,username,normalized_username,display_name) "
                        + "VALUES ('command-user','Command','command','Command User')");
            }
            CommandResult second = provision(fixture, fixture.password());
            assertEquals(0, second.exitCode(), "Repeated migrateDatabase command must succeed");
            try (var connection = fixture.openConnection(); var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT display_name FROM users WHERE id='command-user'")) {
                assertTrue(rows.next());
                assertEquals("Command User", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void migrateDatabase_wrongPassword_reportsDomainFailureWithoutSecrets() throws Exception {
        copyBuildContext();
        try (PostgresFixture fixture = new PostgresFixture()) {
            String wrongPassword = "wrong_" + fixture.password();
            CommandResult result = provision(fixture, wrongPassword);
            assertTrue(result.output().contains("Database provisioning failed"),
                    "Expected provisioning failure diagnostic; unrelated Gradle errors are not acceptance");
            assertNotEquals(0, result.exitCode());
            assertFalse(result.output().contains(fixture.jdbcUrl()));
            assertFalse(result.output().contains(fixture.username()));
            assertFalse(result.output().contains(fixture.password()));
            assertFalse(result.output().contains(wrongPassword));
            assertFalse(result.output().contains("org.postgresql"));
            assertFalse(result.output().contains("PSQLException"));
        }
    }

    private void copyBuildContext() throws IOException {
        Path repository = Path.of("").toAbsolutePath();
        for (String file : List.of("build.gradle", "settings.gradle", "gradlew", "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")) {
            copy(repository, Path.of(file));
        }
        for (String root : List.of("src/main/java/hotshop/database", "src/main/resources/db")) {
            try (var files = Files.walk(repository.resolve(root))) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".sql"))
                        .toList()) {
                    copy(repository, repository.relativize(file));
                }
            }
        }
        copy(repository, Path.of("src/main/java/hotshop/storage/SupabaseStorageConfig.java"));
    }

    private void copy(Path repository, Path relative) throws IOException {
        Path target = directory.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.copy(repository.resolve(relative), target, StandardCopyOption.REPLACE_EXISTING);
    }

    private CommandResult provision(PostgresFixture fixture, String password) throws Exception {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = windows
                ? List.of("cmd.exe", "/c", "gradlew.bat", "--offline", "--no-daemon", "--console=plain",
                        "migrateDatabase")
                : List.of("sh", "./gradlew", "--offline", "--no-daemon", "--console=plain", "migrateDatabase");
        Path output = Files.createTempFile(directory, "provision-", ".log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().put("HOTSHOP_DB_URL", fixture.jdbcUrl());
        builder.environment().put("HOTSHOP_DB_USER", fixture.username());
        builder.environment().put("HOTSHOP_DB_PASSWORD", password);
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean completed = process.waitFor(180, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
        }
        assertTrue(completed, "Isolated provisioning Gradle process timed out");
        return new CommandResult(process.exitValue(), Files.readString(output));
    }

    private record CommandResult(int exitCode, String output) {
    }
}
