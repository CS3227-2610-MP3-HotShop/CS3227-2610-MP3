package hotshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.llm.LlmClient;
import hotshop.llm.LlmConfiguration;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.service.DescriptionService;
import hotshop.service.DescriptionService.FailureKind;
import hotshop.service.ListingDraft;

/** Exercises runtime ownership using isolated SQLite data and controlled transports. */
class DescriptionRuntimeTest {
    private static final Clock CLOCK = Clock.systemUTC();
    private static final String DESCRIPTION = "A wooden chair in good condition.";

    @TempDir
    Path directory;

    @Test
    void open_missingConfiguration_preservesManualListingAndReturnsUnavailable() throws Exception {
        try (DescriptionRuntime owner = new DescriptionRuntime(LlmConfiguration.load(directory));
                ApplicationRuntime runtime = ApplicationRuntime.open(directory.resolve("data"), CLOCK, owner)) {
            loginNew(runtime);
            assertFailed(runtime.getDescriptions().generateDescription("Chair", Condition.GOOD),
                    FailureKind.UNAVAILABLE);
            var saved = runtime.getListings().createListing(new ListingDraft("Chair", "Manual description",
                    Category.FURNITURE, 500, Condition.GOOD, "Library"), List.of()).get(10, TimeUnit.SECONDS);
            assertEquals("Manual description", saved.listing().getDetails().description());
            assertEquals(1, runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS).size());
        }
    }

    @Test
    void open_existingOverloads_exposesOfflineDescriptionService() throws Exception {
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory.resolve("default"))) {
            assertNotNull(runtime.getDescriptions());
        }
        try (ApplicationRuntime runtime = ApplicationRuntime.open(directory.resolve("clock"), CLOCK)) {
            loginNew(runtime);
            assertFailed(runtime.getDescriptions().generateDescription("Chair", Condition.GOOD),
                    FailureKind.UNAVAILABLE);
        }
    }

    @Test
    void generateDescription_injectedClient_usesRuntimeSessionAndDoesNotPersist() throws Exception {
        FakeClient client = new FakeClient();
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime runtime = ApplicationRuntime.open(directory, CLOCK, owner)) {
            assertFailed(runtime.getDescriptions().generateDescription("Chair", Condition.GOOD),
                    FailureKind.LOGIN_REQUIRED);
            assertEquals(0, client.dispatches.get());
            loginNew(runtime);
            var generated = runtime.getDescriptions().generateDescription("Chair", Condition.GOOD);
            client.next().complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new DescriptionService.Generated(DESCRIPTION), generated.get(10, TimeUnit.SECONDS));
            assertTrue(runtime.getListings().getMyListings().get(10, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void generateDescription_twoRuntimeSessions_remainIndependent() throws Exception {
        FakeClient client = new FakeClient();
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime first = ApplicationRuntime.open(directory.resolve("first"), CLOCK, owner);
                ApplicationRuntime second = ApplicationRuntime.open(directory.resolve("second"), CLOCK, owner)) {
            loginNew(first);
            assertFailed(second.getDescriptions().generateDescription("Chair", Condition.GOOD),
                    FailureKind.LOGIN_REQUIRED);
            assertEquals(0, client.dispatches.get());
        }
    }

    @Test
    void generateDescription_sameAccountAcrossRuntimes_sharesActiveAdmission() throws Exception {
        seedCopies();
        FakeClient client = new FakeClient();
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime first = openCopy("first", owner);
                ApplicationRuntime second = openCopy("second", owner)) {
            var pending = first.getDescriptions().generateDescription("Chair", Condition.GOOD);
            CompletableFuture<LlmClient.Result> transport = client.next();
            assertFailed(second.getDescriptions().generateDescription("Chair", Condition.GOOD),
                    FailureKind.IN_FLIGHT);
            transport.complete(new LlmClient.Generated(DESCRIPTION));
            assertEquals(new DescriptionService.Generated(DESCRIPTION), pending.get(10, TimeUnit.SECONDS));
            assertEquals(1, client.dispatches.get());
        }
    }

    @Test
    void close_oneRuntime_preservesSharedQuotaAndOtherRuntimeTransport() throws Exception {
        seedCopies();
        FakeClient client = new FakeClient();
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime first = openCopy("first", owner);
                ApplicationRuntime second = openCopy("second", owner)) {
            for (int index = 0; index < 5; index++) {
                var result = (index % 2 == 0 ? first : second).getDescriptions()
                        .generateDescription("Chair", Condition.GOOD);
                client.next().complete(new LlmClient.Generated(DESCRIPTION));
                assertEquals(new DescriptionService.Generated(DESCRIPTION), result.get(10, TimeUnit.SECONDS));
            }
            first.close();
            assertEquals(0, client.closes.get());
            assertFailed(second.getDescriptions().generateDescription("Chair", Condition.GOOD),
                    FailureKind.RATE_LIMITED);
            assertEquals(5, client.dispatches.get());
        }
        assertEquals(1, client.closes.get());
    }

    @Test
    void close_pendingAttempt_cancelsTransportRejectsNewGenerationAndReleasesDirectory() throws Exception {
        FakeClient client = new FakeClient();
        try (DescriptionRuntime owner = new DescriptionRuntime(client)) {
            ApplicationRuntime runtime = ApplicationRuntime.open(directory, CLOCK, owner);
            loginNew(runtime);
            var pending = runtime.getDescriptions().generateDescription("Chair", Condition.GOOD);
            var transport = client.next();
            runtime.close();
            runtime.close();
            assertTrue(transport.isCancelled());
            assertFailed(pending, FailureKind.CLOSED);
            assertFailed(runtime.getDescriptions().generateDescription("Chair", Condition.GOOD), FailureKind.CLOSED);
            assertEquals(0, client.closes.get());
            try (ApplicationRuntime reopened = ApplicationRuntime.open(directory, CLOCK, owner)) {
                assertNotNull(reopened.getAccounts());
            }
        }
    }

    @Test
    void close_serverOwner_cancelsAllPendingServicesAndClosesClientOnce() throws Exception {
        FakeClient client = new FakeClient();
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime first = ApplicationRuntime.open(directory.resolve("first"), CLOCK, owner);
                ApplicationRuntime second = ApplicationRuntime.open(directory.resolve("second"), CLOCK, owner)) {
            loginNew(first);
            loginNew(second);
            var firstResult = first.getDescriptions().generateDescription("Chair", Condition.GOOD);
            var firstTransport = client.next();
            var secondResult = second.getDescriptions().generateDescription("Desk", Condition.GOOD);
            var secondTransport = client.next();
            owner.close();
            owner.close();
            assertFailed(firstResult, FailureKind.CLOSED);
            assertFailed(secondResult, FailureKind.CLOSED);
            assertTrue(firstTransport.isCancelled());
            assertTrue(secondTransport.isCancelled());
            assertEquals(1, client.closes.get());
            assertFailed(first.getDescriptions().generateDescription("Chair", Condition.GOOD), FailureKind.CLOSED);
            assertTrue(first.getAccounts().getCurrentUserId().get(10, TimeUnit.SECONDS).isPresent());
        }
    }

    @Test
    void closeAsync_queuedTransportCompletion_returnsClosedInsteadOfGenerated() throws Exception {
        FakeClient client = new FakeClient();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime runtime = ApplicationRuntime.open(directory, CLOCK, owner)) {
            loginNew(runtime);
            var result = runtime.getDescriptions().generateDescription("Chair", Condition.GOOD);
            var transport = client.next();
            var busy = runtime.getAccounts().changePassword("Sample1!", "Other2!Password").thenRun(() -> {
                entered.countDown();
                awaitLatch(release);
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                transport.complete(new LlmClient.Generated(DESCRIPTION));
                assertFalse(result.isDone());
                var close = runtime.closeAsync();
                assertFailed(result, FailureKind.CLOSED);
                release.countDown();
                close.get(10, TimeUnit.SECONDS);
                busy.get(10, TimeUnit.SECONDS);
                assertFailed(result, FailureKind.CLOSED);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void close_pendingPreparation_cancelsPreparationWithoutDispatch() throws Exception {
        FakeClient client = new FakeClient();
        client.preparation = new CompletableFuture<>();
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime runtime = ApplicationRuntime.open(directory, CLOCK, owner)) {
            loginNew(runtime);
            var result = runtime.getDescriptions().generateDescription("Chair", Condition.GOOD);
            assertTrue(client.prepared.await(10, TimeUnit.SECONDS));
            runtime.close();
            assertTrue(client.preparation.isCancelled());
            assertFailed(result, FailureKind.CLOSED);
            assertEquals(0, client.dispatches.get());
        }
    }

    @Test
    void closeAsync_workerBusy_returnsBeforeDrainAndRejectsGenerationImmediately() throws Exception {
        FakeClient client = new FakeClient();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (DescriptionRuntime owner = new DescriptionRuntime(client);
                ApplicationRuntime runtime = ApplicationRuntime.open(directory, CLOCK, owner)) {
            var busy = runtime.getAccounts().register("alice", "Sample1!", "Alice").thenRun(() -> {
                entered.countDown();
                awaitLatch(release);
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                var close = CompletableFuture.supplyAsync(runtime::closeAsync).get(2, TimeUnit.SECONDS);
                assertFalse(close.isDone());
                assertSame(close, runtime.closeAsync());
                assertThrows(IOException.class, () -> ApplicationRuntime.open(directory));
                assertFailed(runtime.getDescriptions().generateDescription("Chair", Condition.GOOD),
                        FailureKind.CLOSED);
                release.countDown();
                close.get(10, TimeUnit.SECONDS);
                busy.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        try (ApplicationRuntime reopened = ApplicationRuntime.open(directory)) {
            assertNotNull(reopened.getAccounts());
        }
    }

    @Test
    void open_closedServerOwner_rejectsAdmissionAndReleasesDirectoryLock() throws Exception {
        FakeClient client = new FakeClient();
        DescriptionRuntime owner = new DescriptionRuntime(client);
        owner.close();
        assertThrows(IllegalStateException.class, () -> ApplicationRuntime.open(directory, CLOCK, owner));
        try (ApplicationRuntime reopened = ApplicationRuntime.open(directory)) {
            assertNotNull(reopened.getAccounts());
        }
        assertEquals(1, client.closes.get());
    }

    private void seedCopies() throws Exception {
        Path seed = directory.resolve("seed");
        try (ApplicationRuntime runtime = ApplicationRuntime.open(seed)) {
            loginNew(runtime);
        }
        for (String name : List.of("first", "second")) {
            Path target = directory.resolve(name);
            Files.createDirectories(target);
            Files.copy(seed.resolve("marketplace.db"), target.resolve("marketplace.db"));
        }
    }

    private ApplicationRuntime openCopy(String name, DescriptionRuntime owner) throws Exception {
        ApplicationRuntime runtime = ApplicationRuntime.open(directory.resolve(name), CLOCK, owner);
        runtime.getAccounts().login("alice", "Sample1!").get(10, TimeUnit.SECONDS);
        return runtime;
    }

    private static void loginNew(ApplicationRuntime runtime) throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").get(10, TimeUnit.SECONDS);
        runtime.getAccounts().login("alice", "Sample1!").get(10, TimeUnit.SECONDS);
    }

    private static void assertFailed(CompletableFuture<DescriptionService.GenerationResult> result,
            FailureKind kind) throws Exception {
        var outcome = result.get(10, TimeUnit.SECONDS);
        assertTrue(outcome instanceof DescriptionService.Failed, () -> "Expected safe failure, got " + outcome);
        assertEquals(kind, ((DescriptionService.Failed) outcome).kind());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Worker gate timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class FakeClient implements LlmClient, AutoCloseable {
        private final BlockingQueue<CompletableFuture<Result>> attempts = new LinkedBlockingQueue<>();
        private final AtomicInteger dispatches = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final CountDownLatch prepared = new CountDownLatch(1);
        private CompletableFuture<PreparedRequest> preparation;

        @Override
        public CompletableFuture<Result> generate(String title, Condition condition) {
            return prepare(title, condition).thenCompose(prepared -> prepared.dispatch().result());
        }

        @Override
        public CompletableFuture<PreparedRequest> prepare(String title, Condition condition) {
            prepared.countDown();
            if (preparation != null) {
                return preparation;
            }
            return CompletableFuture.completedFuture(beforeSend -> {
                beforeSend.run();
                dispatches.incrementAndGet();
                CompletableFuture<Result> completion = new CompletableFuture<>();
                attempts.add(completion);
                return new Attempt() {
                    @Override
                    public CompletableFuture<Result> result() {
                        return completion;
                    }

                    @Override
                    public boolean wasDispatched() {
                        return true;
                    }
                };
            });
        }

        CompletableFuture<Result> next() throws InterruptedException {
            var result = attempts.poll(10, TimeUnit.SECONDS);
            assertNotNull(result, "Expected a dispatched fake request");
            return result;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
