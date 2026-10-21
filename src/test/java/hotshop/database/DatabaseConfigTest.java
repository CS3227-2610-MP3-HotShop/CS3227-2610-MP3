package hotshop.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseConfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"SSLMODE", "%73slmode"})
    void fromEnvironment_noncanonicalHostedTlsKey_rejectsConfiguration(String key) {
        Map<String, String> environment = environment();
        environment.put("HOTSHOP_DB_URL", "jdbc:postgresql://synthetic.example.invalid/test?"
                + key + "=verify-full");
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnvironment(environment));
    }

    @Test
    void fromEnvironment_hostedVerifyFull_acceptsWithoutConnecting() {
        Map<String, String> environment = environment();
        String url = "jdbc:postgresql://synthetic.example.invalid/test?sslmode=verify-full";
        environment.put("HOTSHOP_DB_URL", url);
        DatabaseConfig config = DatabaseConfig.fromEnvironment(environment);
        assertEquals(url, config.url());
        assertEquals("synthetic_user", config.username());
        assertEquals("synthetic_password", config.password());
    }

    @Test
    void toString_directConfiguration_redactsValuesWithoutLoader() {
        DatabaseConfig config = new DatabaseConfig("jdbc:postgresql://synthetic.example.invalid/test",
                "synthetic_user", "synthetic_password");
        assertFalse(config.toString().contains(config.url()));
        assertFalse(config.toString().contains(config.username()));
        assertFalse(config.toString().contains(config.password()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOTSHOP_DB_URL", "HOTSHOP_DB_USER", "HOTSHOP_DB_PASSWORD"})
    void fromEnvironment_missingValue_rejectsConfiguration(String key) {
        Map<String, String> environment = environment();
        environment.remove(key);
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnvironment(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOTSHOP_DB_URL", "HOTSHOP_DB_USER", "HOTSHOP_DB_PASSWORD"})
    void fromEnvironment_blankValue_rejectsConfiguration(String key) {
        Map<String, String> environment = environment();
        environment.put(key, " ");
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnvironment(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:sqlite:test.db", "postgresql://localhost/test",
            "jdbc:postgresql://user:password@localhost/test",
            "jdbc:postgresql://localhost/test?user=secret",
            "jdbc:postgresql://localhost/test?password=secret",
            "jdbc:postgresql://localhost/test?sslpassword=secret",
            "jdbc:postgresql://example.invalid/test?sslmode=disable"})
    void fromEnvironment_invalidUrl_rejectsConfiguration(String url) {
        Map<String, String> environment = environment();
        environment.put("HOTSHOP_DB_URL", url);
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnvironment(environment));
    }

    @Test
    void fromEnvironment_validLocalValues_preservesSeparateCredentials() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(environment());
        assertEquals("jdbc:postgresql://127.0.0.1:5432/test", config.url());
        assertEquals("synthetic_user", config.username());
        assertEquals("synthetic_password", config.password());
    }

    @Test
    void toString_validConfiguration_redactsConnectionAndCredentials() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(environment());
        assertFalse(config.toString().contains(config.url()));
        assertFalse(config.toString().contains(config.username()));
        assertFalse(config.toString().contains(config.password()));
    }

    @Test
    void fromEnvironment_invalidUrl_redactsValidationFailure() {
        Map<String, String> environment = environment();
        environment.put("HOTSHOP_DB_URL", "jdbc:postgresql://synthetic_user:synthetic_password@localhost/test");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromEnvironment(environment));
        for (Throwable detail = failure; detail != null; detail = detail.getCause()) {
            assertFalse(detail.toString().contains("synthetic_password"));
            assertFalse(detail.toString().contains("synthetic_user"));
        }
    }

    private static Map<String, String> environment() {
        return new HashMap<>(Map.of("HOTSHOP_DB_URL", "jdbc:postgresql://127.0.0.1:5432/test",
                "HOTSHOP_DB_USER", "synthetic_user", "HOTSHOP_DB_PASSWORD", "synthetic_password"));
    }
}
