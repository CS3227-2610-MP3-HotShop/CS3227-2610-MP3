package hotshop.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;

import hotshop.ApplicationRuntime;
import hotshop.DescriptionRuntime;
import hotshop.model.Condition;
import hotshop.service.DescriptionService;

/** Explicitly configured live-only adapter through the authenticated application service. */
public final class ProductionLiveLlmClientFactory implements LiveLlmClientFactory {
    static final long SPACING_NANOS = TimeUnit.SECONDS.toNanos(13);
    private final AtomicInteger dispatches = new AtomicInteger();
    private final Function<LlmConfiguration, HttpLlmClient> transportFactory;
    private final EvidenceWriter writer;
    private final DataCleaner cleaner;
    private DescriptionRuntime descriptions;
    private ApplicationRuntime runtime;
    private Path temporaryData;
    private Path evidence;
    private String version;
    private long terminalNanos;
    private int cases;
    private boolean manifestOwned;
    private boolean closed;

    public ProductionLiveLlmClientFactory() {
        this(HttpLlmClient::new, (path, text, createNew) -> {
            if (createNew) {
                Files.writeString(path, text, StandardOpenOption.CREATE_NEW);
            } else {
                Files.writeString(path, text);
            }
        }, ProductionLiveLlmClientFactory::deleteTemporaryData);
    }

    ProductionLiveLlmClientFactory(Function<LlmConfiguration, HttpLlmClient> transportFactory,
            EvidenceWriter writer, DataCleaner cleaner) {
        this.transportFactory = transportFactory;
        this.writer = writer;
        this.cleaner = cleaner;
    }

    @Override
    public String evaluationVersion() {
        return System.getProperty("hotshop.llm.live.runId");
    }

    @Override
    public LlmClient create() throws Exception {
        return open(Path.of("."));
    }

    LlmClient open(Path configurationDirectory) throws Exception {
        try {
            version = evaluationVersion();
            String location = System.getProperty("hotshop.llm.live.evidenceDir");
            if (version == null || !version.matches("[A-Za-z0-9._-]{1,80}") || location == null
                    || runtime != null || closed) {
                throw preflight();
            }
            evidence = Path.of(location).toAbsolutePath().normalize();
            if (!Files.isDirectory(evidence, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(evidence)
                    || Files.exists(evidence.resolve("manifest.json"))) {
                throw preflight();
            }
            for (var item : LlmSecurityCorpus.load()) {
                if (Files.exists(evidence.resolve(item.id() + ".json"), LinkOption.NOFOLLOW_LINKS)) {
                    throw preflight();
                }
            }
            LlmConfiguration configuration = LlmConfiguration.load(configurationDirectory);
            if (!configuration.isAvailable()) {
                throw preflight();
            }
            writeManifest(true);
            manifestOwned = true;
            descriptions = new DescriptionRuntime(new CountingClient(transportFactory.apply(configuration)));
            temporaryData = Files.createTempDirectory("hotshop-live-synthetic-");
            runtime = ApplicationRuntime.open(temporaryData, Clock.systemUTC(), descriptions);
            runtime.getAccounts().register("live_evaluation", "Synthetic1!", "Synthetic evaluation")
                    .get(20, TimeUnit.SECONDS);
            runtime.getAccounts().login("live_evaluation", "Synthetic1!").get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                // Preserve the primary fixed preflight failure without private nested diagnostics.
            }
            throw preflight();
        }
        return new LlmClient() {
            @Override
            public CompletableFuture<Result> generate(String title, Condition condition) {
                return generateCase(title, condition);
            }

            @Override
            public CompletableFuture<PreparedRequest> prepare(String title, Condition condition) {
                return CompletableFuture.failedFuture(new PreparationException(Failure.UNAVAILABLE));
            }
        };
    }

    private CompletableFuture<LlmClient.Result> generateCase(String title, Condition condition) {
        try {
            long delay = remainingDelay(terminalNanos, System.nanoTime());
            if (delay > 0) {
                TimeUnit.NANOSECONDS.sleep(delay);
            }
            LlmSecurityCorpus.Case item = LlmSecurityCorpus.load().stream()
                    .filter(row -> row.title().equals(title) && row.condition() == condition).findFirst().orElseThrow();
            Instant started = Instant.now();
            return runtime.getDescriptions().generateDescription(title, condition).thenApply(result -> {
                terminalNanos = System.nanoTime();
                try {
                    record(item, result, started);
                } catch (Exception failure) {
                    throw new IllegalStateException("Live evaluation evidence write failed");
                }
                if (result instanceof DescriptionService.Generated generated) {
                    return new LlmClient.Generated(generated.description());
                }
                DescriptionService.FailureKind kind = ((DescriptionService.Failed) result).kind();
                LlmClient.Failure mapped;
                try {
                    mapped = LlmClient.Failure.valueOf(kind.name());
                } catch (IllegalArgumentException localRejection) {
                    mapped = LlmClient.Failure.UNAVAILABLE;
                }
                return new LlmClient.Failed(mapped);
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(new IllegalStateException("Live evaluation interrupted"));
        } catch (Exception failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Live evaluation setup failed"));
        }
    }

    static long remainingDelay(long previousTerminal, long now) {
        return previousTerminal == 0 ? 0 : Math.max(0, SPACING_NANOS - (now - previousTerminal));
    }

    static String classification(DescriptionService.GenerationResult result) {
        if (result instanceof DescriptionService.Generated) {
            return "UNREVIEWED";
        }
        return ((DescriptionService.Failed) result).kind() == DescriptionService.FailureKind.INVALID_RESPONSE
                ? "STRUCTURAL_REJECTION" : "UNVERIFIED";
    }

    private void record(LlmSecurityCorpus.Case item, DescriptionService.GenerationResult result, Instant started)
            throws IOException {
        JsonObject row = new JsonObject();
        row.addProperty("runId", version);
        row.addProperty("caseId", item.id());
        row.addProperty("title", item.title());
        row.addProperty("condition", item.condition().name());
        row.addProperty("control", item.control());
        row.addProperty("rubric", item.review());
        row.addProperty("started", started.toString());
        row.addProperty("terminal", Instant.now().toString());
        row.addProperty("assessment", classification(result));
        if (result instanceof DescriptionService.Generated generated) {
            row.addProperty("outcome", "GENERATED");
            row.addProperty("description", generated.description());
        } else {
            row.addProperty("outcome", ((DescriptionService.Failed) result).kind().name());
        }
        row.addProperty("cumulativeDispatches", dispatches.get());
        writeManifest(false);
        try {
            writer.write(evidence.resolve(item.id() + ".json"), row.toString(), true);
            cases++;
        } finally {
            writeManifest(false);
        }
    }

    private void writeManifest(boolean createNew) throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("runId", version);
        manifest.addProperty("recordedCases", cases);
        manifest.addProperty("actualDispatches", dispatches.get());
        manifest.addProperty("requiredCases", 26);
        manifest.addProperty("humanReview", "PENDING");
        manifest.addProperty("providerToolNonUse", "UNVERIFIED");
        writer.write(evidence.resolve("manifest.json"), manifest.toString(), createNew);
    }

    private static IllegalStateException preflight() {
        return new IllegalStateException("Live evaluation preflight failed");
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        boolean failed = false;
        try {
            if (runtime != null) {
                runtime.close();
            }
        } catch (Exception failure) {
            failed = true;
        }
        try {
            if (descriptions != null) {
                descriptions.close();
            }
        } catch (Exception failure) {
            failed = true;
        }
        try {
            if (manifestOwned) {
                writeManifest(false);
            }
        } catch (Exception failure) {
            failed = true;
        }
        try {
            if (temporaryData != null) {
                cleaner.clean(temporaryData);
            }
        } catch (Exception failure) {
            failed = true;
        }
        if (failed) {
            throw new IllegalStateException("Live evaluation cleanup failed");
        }
    }

    static void deleteTemporaryData(Path directory) throws IOException {
        Path target = directory.toAbsolutePath().normalize();
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!temporaryRoot.equals(target.getParent())
                || !target.getFileName().toString().startsWith("hotshop-live-synthetic-")
                || Files.isSymbolicLink(target)) {
            throw new IOException("Live evaluation temporary cleanup target rejected");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Live evaluation temporary cleanup target rejected");
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    interface EvidenceWriter {
        void write(Path path, String text, boolean createNew) throws IOException;
    }

    @FunctionalInterface
    interface DataCleaner {
        void clean(Path directory) throws IOException;
    }

    private final class CountingClient implements LlmClient, AutoCloseable {
        private final HttpLlmClient delegate;

        CountingClient(HttpLlmClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableFuture<Result> generate(String title, Condition condition) {
            return CompletableFuture.failedFuture(new PreparationException(Failure.UNAVAILABLE));
        }

        @Override
        public CompletableFuture<PreparedRequest> prepare(String title, Condition condition) {
            return delegate.prepare(title, condition).thenApply(prepared -> beforeSend -> prepared.dispatch(() -> {
                beforeSend.run();
                dispatches.incrementAndGet();
            }));
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
