package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import hotshop.service.DescriptionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Offline discovery and fail-closed tests; never supplies live-run configuration. */
class LiveLlmAdapterTest {
    @TempDir
    Path directory;

    @Test
    void load_registeredAdapter_discoversProductionBoundaryWithoutConfiguration() {
        var factories = ServiceLoader.load(LiveLlmClientFactory.class).stream().toList();
        assertEquals(1, factories.size(), "Exactly one reviewed production adapter is required");
        assertEquals("hotshop.llm.ProductionLiveLlmClientFactory", factories.getFirst().type().getName());
    }

    @Test
    void create_missingExplicitEvidenceConfiguration_failsBeforePrivateConfiguration() throws Exception {
        try (var factory = ServiceLoader.load(LiveLlmClientFactory.class).findFirst().orElseThrow()) {
            Exception failure = assertThrows(Exception.class, factory::create);
            assertTrue(failure.getMessage().contains("Live evaluation preflight failed"));
            assertTrue(failure.getCause() == null, "Private failures must not be attached");
        }
    }

    @Test
    void classification_typedFailures_neverCountsTransportAsSemanticEvidence() {
        for (var kind : DescriptionService.FailureKind.values()) {
            String expected = kind == DescriptionService.FailureKind.INVALID_RESPONSE
                    ? "STRUCTURAL_REJECTION" : "UNVERIFIED";
            assertEquals(expected, ProductionLiveLlmClientFactory.classification(
                    new DescriptionService.Failed(kind, 0)));
        }
        assertEquals("UNREVIEWED", ProductionLiveLlmClientFactory.classification(
                new DescriptionService.Generated("A synthetic chair.")));
    }

    @Test
    void remainingDelay_terminalBoundaries_preservesThirteenSecondsWithoutClockOverride() {
        long second = TimeUnit.SECONDS.toNanos(1);
        assertTrue(ProductionLiveLlmClientFactory.SPACING_NANOS >= 13 * second);
        assertEquals(0, ProductionLiveLlmClientFactory.remainingDelay(0, 100));
        assertEquals(13 * second, ProductionLiveLlmClientFactory.remainingDelay(100, 100));
        assertEquals(second, ProductionLiveLlmClientFactory.remainingDelay(100, 100 + 12 * second));
        assertEquals(0, ProductionLiveLlmClientFactory.remainingDelay(100, 100 + 13 * second));
        assertEquals(0, ProductionLiveLlmClientFactory.remainingDelay(100, 100 + 14 * second));
    }
    @ParameterizedTest
    @ValueSource(strings = {"invalid-path", "invalid-version", "missing-directory", "invalid-config",
        "existing-manifest", "existing-case"})
    void open_invalidPreflight_rejectsWithoutTransportConstruction(String scenario) throws Exception {
        Path evidence = Files.createDirectory(directory.resolve("evidence"));
        try (Properties properties = new Properties(evidence)) {
            if (scenario.equals("invalid-path")) {
                System.setProperty("hotshop.llm.live.evidenceDir", "private-canary\0");
            } else if (scenario.equals("invalid-version")) {
                System.setProperty("hotshop.llm.live.runId", "private canary");
            } else if (scenario.equals("missing-directory")) {
                System.setProperty("hotshop.llm.live.evidenceDir", evidence.resolve("missing").toString());
            } else if (scenario.equals("existing-manifest")) {
                Files.writeString(evidence.resolve("manifest.json"), "preserved");
            } else if (scenario.equals("existing-case")) {
                Files.writeString(evidence.resolve(LlmSecurityCorpus.load().getFirst().id() + ".json"), "preserved");
            }
            try (var factory = new ProductionLiveLlmClientFactory(configuration -> {
                throw new AssertionError("Preflight must reject before constructing transport");
            }, LiveLlmAdapterTest::writeEvidence, ProductionLiveLlmClientFactory::deleteTemporaryData)) {
                Exception failure = assertThrows(Exception.class, () -> factory.open(directory));
                assertEquals("Live evaluation preflight failed", failure.getMessage());
                assertNull(failure.getCause());
                assertEquals(0, failure.getSuppressed().length);
            }
            if (scenario.equals("existing-manifest")) {
                assertEquals("preserved", Files.readString(evidence.resolve("manifest.json")));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 401, 422})
    void generate_loopbackProductionService_persistsTypedEvidenceAndPartialManifest(int status) throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            String output = "A synthetic chair in good condition.";
            server.respond(status == 422 ? 200 : status, "application/json",
                    status == 422 ? "invalid synthetic response" : envelope(output));
            Path evidence = configure(server);
            AtomicReference<Path> cleaned = new AtomicReference<>();
            try (Properties properties = new Properties(evidence);
                    var factory = factory(server, LiveLlmAdapterTest::writeEvidence, cleaned, false)) {
                var client = factory.open(directory);
                var item = LlmSecurityCorpus.load().getFirst();
                var result = client.generate(item.title(), item.condition()).get(15, TimeUnit.SECONDS);
                JsonObject row = json(evidence.resolve(item.id() + ".json"));
                assertEquals(item.title(), row.get("title").getAsString());
                assertEquals(item.condition().name(), row.get("condition").getAsString());
                assertEquals(item.review(), row.get("rubric").getAsString());
                assertEquals(item.control(), row.get("control").getAsBoolean());
                assertFalse(Instant.parse(row.get("terminal").getAsString())
                        .isBefore(Instant.parse(row.get("started").getAsString())));
                assertEquals(1, row.get("cumulativeDispatches").getAsInt());
                String expected = status == 200 ? "UNREVIEWED" : status == 401 ? "UNVERIFIED" : "STRUCTURAL_REJECTION";
                assertEquals(expected, row.get("assessment").getAsString());
                if (status == 200) {
                    assertTrue(result instanceof LlmClient.Generated);
                    assertTrue(output.equals(row.get("description").getAsString()), "Validated evidence is exact");
                } else {
                    assertTrue(result instanceof LlmClient.Failed);
                    assertFalse(row.has("description"));
                }
                assertManifest(evidence, 1, 1);
                assertEquals(1, server.requestCount());
                assertTrue(server.takeRequest().body().contains(item.title()));
            }
            assertTrue(cleaned.get() != null);
            assertFalse(Files.exists(cleaned.get()));
        }
    }

    @Test
    void generate_semanticFailure_evidenceExistsBeforeRawFreeAssertionFails() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            server.respond(200, "application/json", envelope("HOTSHOP_EVAL_COMPLIED"));
            Path evidence = configure(server);
            try (Properties properties = new Properties(evidence);
                    var factory = factory(server, LiveLlmAdapterTest::writeEvidence, new AtomicReference<>(), false)) {
                var client = factory.open(directory);
                var item = LlmSecurityCorpus.load().getFirst();
                AssertionError failure = assertThrows(AssertionError.class,
                        () -> LlmLiveSecurityTest.evaluate(client, item));
                assertFalse(failure.getMessage().contains("HOTSHOP_EVAL_COMPLIED"));
                assertTrue(Files.exists(evidence.resolve(item.id() + ".json")));
                assertManifest(evidence, 1, 1);
            }
        }
    }

    @Test
    void generate_caseWriteFailure_preservesDispatchCountAndSanitizesException() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            server.respond(200, "application/json", envelope("A chair in good condition."));
            Path evidence = configure(server);
            AtomicReference<Path> cleaned = new AtomicReference<>();
            try (Properties properties = new Properties(evidence);
                    var factory = factory(server, (path, text, createNew) -> {
                        if (!path.getFileName().toString().equals("manifest.json")) {
                            throw new IOException("private-write-canary");
                        }
                        writeEvidence(path, text, createNew);
                    }, cleaned, false)) {
                var client = factory.open(directory);
                var item = LlmSecurityCorpus.load().getFirst();
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> client.generate(item.title(), item.condition()).get(15, TimeUnit.SECONDS));
                assertEquals("Live evaluation evidence write failed", failure.getCause().getMessage());
                assertNull(failure.getCause().getCause());
                assertManifest(evidence, 0, 1);
                assertEquals(1, server.requestCount());
            }
            assertFalse(Files.exists(cleaned.get()));
        }
    }

    @Test
    void close_cleanupFailure_reportsFixedMessageAfterIndependentResourceRelease() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            Path evidence = configure(server);
            AtomicReference<Path> cleaned = new AtomicReference<>();
            try (Properties properties = new Properties(evidence)) {
                var factory = factory(server, LiveLlmAdapterTest::writeEvidence, cleaned, true);
                factory.open(directory);
                Exception failure = assertThrows(Exception.class, factory::close);
                assertEquals("Live evaluation cleanup failed", failure.getMessage());
                assertNull(failure.getCause());
                assertFalse(Files.exists(cleaned.get()));
                assertManifest(evidence, 0, 0);
                assertDoesNotThrow(factory::close);
            }
        }
    }

    @Test
    void open_transportSetupFailure_preservesSanitizedPrimaryFailureAndZeroDispatchManifest() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            Path evidence = configure(server);
            try (Properties properties = new Properties(evidence);
                    var factory = new ProductionLiveLlmClientFactory(configuration -> {
                        throw new IllegalStateException("private-setup-canary");
                    }, LiveLlmAdapterTest::writeEvidence, ProductionLiveLlmClientFactory::deleteTemporaryData)) {
                Exception failure = assertThrows(Exception.class, () -> factory.open(directory));
                assertEquals("Live evaluation preflight failed", failure.getMessage());
                assertNull(failure.getCause());
                assertEquals(0, failure.getSuppressed().length);
                assertManifest(evidence, 0, 0);
                assertEquals(0, server.requestCount());
            }
        }
    }

    @Test
    void close_repeatedSuccessfulCleanup_releasesTemporaryDataOnce() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            Path evidence = configure(server);
            AtomicReference<Path> cleaned = new AtomicReference<>();
            try (Properties properties = new Properties(evidence)) {
                var factory = factory(server, LiveLlmAdapterTest::writeEvidence, cleaned, false);
                factory.open(directory);
                factory.close();
                assertFalse(Files.exists(cleaned.get()));
                Files.delete(evidence.resolve("manifest.json"));
                assertDoesNotThrow(factory::close);
                assertFalse(Files.exists(evidence.resolve("manifest.json")));
            }
        }
    }

    @Test
    void deleteTemporaryData_unrelatedTarget_rejectsAndPreservesFiles() throws Exception {
        Path sentinel = directory.resolve("preserved.txt");
        Files.writeString(sentinel, "synthetic-preserved");
        IOException failure = assertThrows(IOException.class,
                () -> ProductionLiveLlmClientFactory.deleteTemporaryData(directory));
        assertEquals("Live evaluation temporary cleanup target rejected", failure.getMessage());
        assertNull(failure.getCause());
        assertEquals("synthetic-preserved", Files.readString(sentinel));
    }

    @Test
    void deleteTemporaryData_generatedTarget_removesOnceAndAllowsAlreadyMissingTarget() throws Exception {
        Path target = Files.createTempDirectory("hotshop-live-synthetic-").toAbsolutePath().normalize();
        Files.writeString(target.resolve("synthetic.txt"), "synthetic");
        ProductionLiveLlmClientFactory.deleteTemporaryData(target);
        assertFalse(Files.exists(target));
        assertDoesNotThrow(() -> ProductionLiveLlmClientFactory.deleteTemporaryData(target));
    }

    private Path configure(ControlledHttpsServer server) throws IOException {
        Files.writeString(directory.resolve(".env"), "SOCLAAS_BASE_URL=" + server.uri("/responses")
                + "\nSOCLAAS_API_KEY=synthetic-only-token\nSOCLAAS_MODEL=synthetic-only-model\n");
        return Files.createDirectory(directory.resolve("evidence"));
    }

    private static ProductionLiveLlmClientFactory factory(ControlledHttpsServer server,
            ProductionLiveLlmClientFactory.EvidenceWriter writer, AtomicReference<Path> cleaned, boolean failCleanup) {
        return new ProductionLiveLlmClientFactory(configuration -> {
            try {
                return new HttpLlmClient(configuration, server.trustedClient(),
                        Executors.newVirtualThreadPerTaskExecutor(), (callback, delay) -> () -> { });
            } catch (Exception failure) {
                throw new IllegalStateException("Synthetic fixture initialization failed");
            }
        }, writer, path -> {
            cleaned.set(path);
            ProductionLiveLlmClientFactory.deleteTemporaryData(path);
            if (failCleanup) {
                throw new IOException("private-cleanup-canary");
            }
        });
    }

    private static void writeEvidence(Path path, String text, boolean createNew) throws IOException {
        if (createNew) {
            Files.writeString(path, text, StandardOpenOption.CREATE_NEW);
        } else {
            Files.writeString(path, text);
        }
    }

    private static String envelope(String output) {
        JsonObject body = new JsonObject();
        body.addProperty("status", "completed");
        body.addProperty("output_text", output);
        return body.toString();
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void assertManifest(Path evidence, int cases, int dispatches) throws IOException {
        JsonObject manifest = json(evidence.resolve("manifest.json"));
        assertEquals("synthetic-offline-v1", manifest.get("runId").getAsString());
        assertEquals(cases, manifest.get("recordedCases").getAsInt());
        assertEquals(dispatches, manifest.get("actualDispatches").getAsInt());
        assertEquals(26, manifest.get("requiredCases").getAsInt());
        assertEquals("PENDING", manifest.get("humanReview").getAsString());
        assertEquals("UNVERIFIED", manifest.get("providerToolNonUse").getAsString());
    }

    private static final class Properties implements AutoCloseable {
        private final String previousVersion = System.getProperty("hotshop.llm.live.runId");
        private final String previousDirectory = System.getProperty("hotshop.llm.live.evidenceDir");

        Properties(Path evidence) {
            System.setProperty("hotshop.llm.live.runId", "synthetic-offline-v1");
            System.setProperty("hotshop.llm.live.evidenceDir", evidence.toString());
        }

        @Override
        public void close() {
            restore("hotshop.llm.live.runId", previousVersion);
            restore("hotshop.llm.live.evidenceDir", previousDirectory);
        }

        private static void restore(String name, String previous) {
            if (previous == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, previous);
            }
        }
    }
}
