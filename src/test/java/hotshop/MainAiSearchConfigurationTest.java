package hotshop;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Covers optional process-environment configuration without opening a network connection. */
class MainAiSearchConfigurationTest {
    @Test
    void createAiSearchClient_missingSetting_keepsAiOptional() {
        assertNull(Main.createAiSearchClient(Map.of()));
        assertNull(Main.createAiSearchClient(Map.of("SOCLAAS_BASE_URL", "https://example.test",
                "SOCLAAS_API_KEY", "test-key")));
        assertNull(Main.createAiSearchClient(Map.of("SOCLAAS_DESCRIPTION_URL", "https://example.test/responses",
                "SOCLAAS_API_KEY", "test-key", "SOCLAAS_MODEL", "test-model")));
    }

    @Test
    void createAiSearchClient_invalidSetting_keepsAiOptional() {
        assertNull(Main.createAiSearchClient(Map.of("SOCLAAS_BASE_URL", "http://example.test",
                "SOCLAAS_API_KEY", "test-key", "SOCLAAS_MODEL", "test-model")));
        assertNull(Main.createAiSearchClient(Map.of("SOCLAAS_BASE_URL", "not a uri",
                "SOCLAAS_API_KEY", "test-key", "SOCLAAS_MODEL", "test-model")));
    }

    @Test
    void createAiSearchClient_validSettings_constructsOwnedClient() {
        var client = Main.createAiSearchClient(Map.of("SOCLAAS_BASE_URL", "https://example.test/v1",
                "SOCLAAS_DESCRIPTION_URL", "https://example.test/responses",
                "SOCLAAS_API_KEY", "test-key", "SOCLAAS_MODEL", "test-model"));
        assertNotNull(client);
        client.close();
    }
}
