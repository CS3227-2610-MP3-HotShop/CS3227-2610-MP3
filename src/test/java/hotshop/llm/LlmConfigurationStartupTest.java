package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmConfigurationStartupTest {
    private static final String ENDPOINT_KEY = "SOCLAAS_BASE_URL";
    private static final String TOKEN_KEY = "SOCLAAS_API_KEY";
    private static final String MODEL_KEY = "SOCLAAS_MODEL";
    private static final String ENDPOINT = "https://startup.invalid/exact?route=a=b";
    private static final String TOKEN = "synthetic-startup-token";
    private static final String MODEL = "synthetic-startup-model";

    @TempDir
    Path directory;

    @Test
    void loadFromEnvironment_realProcessValues_usesEnvironmentWithoutFile() throws Exception {
        runProbe(Map.of(ENDPOINT_KEY, ENDPOINT, TOKEN_KEY, TOKEN, MODEL_KEY, MODEL), "environment", true);
        assertFalse(Files.exists(directory.resolve(".env")));
    }

    @Test
    void loadFromEnvironment_absentProcessValues_usesSyntheticFile() throws Exception {
        writeSettings();
        runProbe(Map.of(), "environment", true);
    }

    @Test
    void loadFromEnvironment_partialProcessValues_doesNotFallBackToFile() throws Exception {
        writeSettings();
        runProbe(Map.of(TOKEN_KEY, TOKEN), "environment", false);
    }

    @Test
    void load_completeProcessValuesWithMissingFile_fileOnlyFactoryIgnoresEnvironment() throws Exception {
        runProbe(Map.of(ENDPOINT_KEY, ENDPOINT, TOKEN_KEY, TOKEN, MODEL_KEY, MODEL), "file", false);
    }

    private void writeSettings() throws Exception {
        Files.writeString(directory.resolve(".env"), ENDPOINT_KEY + "=" + ENDPOINT + "\n"
                + TOKEN_KEY + "=" + TOKEN + "\n" + MODEL_KEY + "=" + MODEL + "\n", StandardCharsets.UTF_8);
    }

    private void runProbe(Map<String, String> environment, String source, boolean available) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        if (!Files.isRegularFile(java)) {
            java = Path.of(System.getProperty("java.home"), "bin", "java");
        }
        String classpath = Path.of(LlmConfiguration.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI()) + File.pathSeparator
                + Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-cp", classpath,
                Probe.class.getName(), directory.toAbsolutePath().toString(), source);
        builder.directory(directory.toFile());
        builder.environment().remove(ENDPOINT_KEY);
        builder.environment().remove(TOKEN_KEY);
        builder.environment().remove(MODEL_KEY);
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Synthetic configuration probe timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(output.contains(ENDPOINT));
            assertFalse(output.contains(TOKEN));
            assertFalse(output.contains(MODEL));
            assertEquals(0, process.exitValue(), "Synthetic configuration probe failed");
            assertEquals(Boolean.toString(available), output.strip());
        } finally {
            process.destroyForcibly();
        }
    }

    /** Minimal isolated JVM entry point; does not open the application or dispatch requests. */
    public static final class Probe {
        /** Checks the public configuration factory using only the explicitly supplied temporary path. */
        public static void main(String[] args) {
            LlmConfiguration configuration = "file".equals(args[1])
                    ? LlmConfiguration.load(Path.of(args[0]))
                    : LlmConfiguration.loadFromEnvironment(Path.of(args[0]));
            if (configuration.isAvailable() && (!ENDPOINT.equals(configuration.endpoint().toString())
                    || !TOKEN.equals(configuration.bearerToken()) || !MODEL.equals(configuration.model()))) {
                throw new IllegalStateException("Unexpected synthetic configuration");
            }
            System.out.println(configuration.isAvailable());
        }
    }
}
