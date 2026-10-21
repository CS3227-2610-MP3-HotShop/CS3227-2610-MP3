package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hotshop.model.Condition;
import hotshop.service.ServiceWorker;

class LlmPreparationTest {
    private static final URI ENDPOINT = URI.create("https://synthetic-preparation.invalid/exact?private=canary");
    private static final String TOKEN = "synthetic-preparation-token-canary";
    private static final String MODEL = "synthetic-preparation-model-canary";
    private static final String TITLE = "Synthetic private chair canary";
    private static final String RESPONSE = "{\"status\":\"completed\",\"output_text\":\"A chair in good condition.\"}";
    private static final LlmClient.Generated GENERATED = new LlmClient.Generated("A chair in good condition.");

    @TempDir
    Path directory;

    @Test
    void prepare_gatedWorker_returnsPendingWithoutCallingHttp() throws Exception {
        ScriptedHttpClient http = http();
        GatedExecutor worker = new GatedExecutor();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker)) {
            var pending = client.prepare(TITLE, Condition.GOOD);
            assertFalse(pending.isDone());
            assertEquals(0, http.calls.get());
            worker.release();
            var prepared = pending.get(5, TimeUnit.SECONDS);
            assertNotEquals(Thread.currentThread(), worker.lastThread);
            assertEquals(0, http.calls.get());
            assertSafe(prepared.toString());
        }
    }

    @Test
    void prepare_serviceWorkerCaller_doesNotBlockAccountQueue() throws Exception {
        ScriptedHttpClient http = http();
        GatedExecutor transport = new GatedExecutor();
        try (ServiceWorker service = new ServiceWorker();
                HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, transport)) {
            var pending = service.submit(() -> client.prepare(TITLE, Condition.GOOD)).get(5, TimeUnit.SECONDS);
            String serviceThread = service.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
            assertFalse(pending.isDone());
            assertTrue(serviceThread.startsWith("hotshop-services"));
            transport.release();
            pending.get(5, TimeUnit.SECONDS);
            assertFalse(transport.lastThread.getName().startsWith("hotshop-services"));
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void prepare_exactly8192Bytes_encodesBeforeDispatchWithoutHttp() throws Exception {
        ScriptedHttpClient http = http();
        try (HttpLlmClient client = client(configuration(ENDPOINT, boundaryModel(8192)), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var prepared = client.prepare("Chair", Condition.GOOD).get(5, TimeUnit.SECONDS);
            assertEquals(0, http.calls.get());
            var attempt = prepared.dispatch();
            assertEquals(GENERATED, attempt.result().get(5, TimeUnit.SECONDS));
            assertTrue(attempt.wasDispatched());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void prepare_over8192Bytes_failsBeforeHttpDispatch() throws Exception {
        ScriptedHttpClient http = http();
        try (HttpLlmClient client = client(configuration(ENDPOINT, boundaryModel(8193)), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> client.prepare("Chair", Condition.GOOD).get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalArgumentException);
            assertNull(failure.getCause().getCause());
            assertSafe(failure.getCause().toString());
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void prepare_missingConfiguration_returnsOnlySafeTypedFailure() throws Exception {
        ScriptedHttpClient http = http();
        try (HttpLlmClient client = client(LlmConfiguration.load(directory), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            assertPreparationFailure(client.prepare(TITLE, Condition.GOOD), LlmClient.Failure.UNAVAILABLE);
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void dispatch_preparedRequest_postsExactConfiguredHeaderAndBody() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            URI endpoint = server.uri("/prepared/exact?route=canary");
            server.respond(200, "application/json", RESPONSE);
            try (HttpLlmClient client = new HttpLlmClient(configuration(endpoint, MODEL), server.trustedClient(),
                    Executors.newVirtualThreadPerTaskExecutor(), scheduler())) {
                var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
                assertEquals(0, server.requestCount());
                var attempt = prepared.dispatch();
                assertEquals(GENERATED, attempt.result().get(10, TimeUnit.SECONDS));
                assertTrue(attempt.wasDispatched());
                var captured = server.takeRequest();
                assertEquals("/prepared/exact?route=canary", captured.uri().toString());
                assertEquals("Bearer " + TOKEN, captured.authorization());
                assertEquals("POST", captured.method());
                assertEquals(new String(client.buildRequest(TITLE, Condition.GOOD), StandardCharsets.UTF_8),
                        captured.body());
                assertSafe(prepared.toString());
                assertSafe(attempt.toString());
            }
        }
    }

    @Test
    void dispatch_networkFailure_marksAttemptDispatchedWithoutRetry() throws Exception {
        ScriptedHttpClient http = http();
        http.failure = new IOException("synthetic-provider-network-canary");
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var attempt = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS).dispatch();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.NETWORK), attempt.result().get(5, TimeUnit.SECONDS));
            assertTrue(attempt.wasDispatched());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void dispatch_providerAuthFailure_marksAttemptDispatched() throws Exception {
        ScriptedHttpClient http = http();
        http.status = 401;
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var attempt = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS).dispatch();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.PROVIDER_AUTH),
                    attempt.result().get(5, TimeUnit.SECONDS));
            assertTrue(attempt.wasDispatched());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void dispatch_afterClientClose_returnsUndispatchedClosedAttempt() throws Exception {
        ScriptedHttpClient http = http();
        HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor());
        var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
        client.close();
        var attempt = prepared.dispatch();
        assertEquals(new LlmClient.Failed(LlmClient.Failure.CLOSED), attempt.result().get(5, TimeUnit.SECONDS));
        assertFalse(attempt.wasDispatched());
        assertEquals(0, http.calls.get());
    }

    @Test
    void dispatch_rejectedWorker_returnsUndispatchedFailure() throws Exception {
        ScriptedHttpClient http = http();
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker)) {
            var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
            worker.shutdown();
            var attempt = prepared.dispatch();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.UNAVAILABLE),
                    attempt.result().get(5, TimeUnit.SECONDS));
            assertFalse(attempt.wasDispatched());
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void dispatch_cancelledQueuedAttempt_doesNotChargeHttpDispatch() throws Exception {
        ScriptedHttpClient http = http();
        GatedExecutor worker = new GatedExecutor();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker)) {
            var preparation = client.prepare(TITLE, Condition.GOOD);
            worker.release();
            var prepared = preparation.get(5, TimeUnit.SECONDS);
            worker.pause();
            var attempt = prepared.dispatch();
            assertFalse(attempt.result().isDone());
            CompletableFuture<Boolean> callback = attempt.result().handle((value, failure) -> {
                worker.release();
                try {
                    return worker.marker().get(5, TimeUnit.SECONDS);
                } catch (Exception error) {
                    throw new AssertionError("Cancellation callback could not drain queued dispatch", error);
                }
            });
            assertTrue(attempt.result().cancel(true));
            assertTrue(callback.get(5, TimeUnit.SECONDS));
            assertFalse(attempt.wasDispatched());
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void dispatch_samePreparedRequestTwice_performsExactlyOneHttpCall() throws Exception {
        ScriptedHttpClient http = http();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
            var first = prepared.dispatch();
            assertEquals(GENERATED, first.result().get(5, TimeUnit.SECONDS));
            var duplicate = prepared.dispatch();
            assertTrue(duplicate.result().get(5, TimeUnit.SECONDS) instanceof LlmClient.Failed);
            assertFalse(duplicate.wasDispatched());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void prepare_closedClient_returnsSafeClosedFailure() throws Exception {
        ScriptedHttpClient http = http();
        HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor());
        client.close();
        assertPreparationFailure(client.prepare(TITLE, Condition.GOOD), LlmClient.Failure.CLOSED);
        assertEquals(0, http.calls.get());
    }

    @Test
    void prepare_rejectedWorker_returnsSafeUnavailableFailure() throws Exception {
        ScriptedHttpClient http = http();
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        worker.shutdown();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker)) {
            assertPreparationFailure(client.prepare(TITLE, Condition.GOOD), LlmClient.Failure.UNAVAILABLE);
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void close_queuedPreparation_terminatesSafelyAndRejectsLatePreparedSuccess() throws Exception {
        ScriptedHttpClient http = http();
        GatedExecutor worker = new GatedExecutor();
        HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker);
        var pending = client.prepare(TITLE, Condition.GOOD);
        assertFalse(pending.isDone());
        List<Runnable> lateTasks = worker.snapshot();
        assertFalse(lateTasks.isEmpty());
        client.close();
        assertPreparationFailure(pending, LlmClient.Failure.CLOSED);
        lateTasks.forEach(Runnable::run);
        assertPreparationFailure(pending, LlmClient.Failure.CLOSED);
        assertEquals(0, http.calls.get());
        assertTrue(worker.isShutdown());
    }

    @Test
    void dispatch_beforeSendHook_runsOnceImmediatelyBeforeHttpOnTransportWorker() throws Exception {
        ScriptedHttpClient http = http();
        AtomicInteger hooks = new AtomicInteger();
        AtomicInteger hooksSeenByHttp = new AtomicInteger(-1);
        AtomicReference<Thread> hookThread = new AtomicReference<>();
        http.beforeReturn = () -> hooksSeenByHttp.set(hooks.get());
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
            var attempt = prepared.dispatch(() -> {
                assertEquals(0, http.calls.get());
                hookThread.set(Thread.currentThread());
                hooks.incrementAndGet();
            });
            assertEquals(GENERATED, attempt.result().get(5, TimeUnit.SECONDS));
            assertTrue(attempt.wasDispatched());
            assertEquals(1, hooks.get());
            assertEquals(1, hooksSeenByHttp.get());
            assertEquals(http.dispatchThread, hookThread.get());
            assertNotEquals(Thread.currentThread(), hookThread.get());
            assertFalse(hookThread.get().getName().startsWith("hotshop-services"));
            var duplicate = prepared.dispatch(hooks::incrementAndGet);
            assertTrue(duplicate.result().get(5, TimeUnit.SECONDS) instanceof LlmClient.Failed);
            assertFalse(duplicate.wasDispatched());
            assertEquals(1, hooks.get());
            assertEquals(1, http.calls.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dispatch_queuedBeforeSendHook_waitsUntilWorkerAndSkipsCancelledAttempt(boolean cancel) throws Exception {
        ScriptedHttpClient http = http();
        GatedExecutor worker = new GatedExecutor();
        AtomicInteger hooks = new AtomicInteger();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker)) {
            var preparation = client.prepare(TITLE, Condition.GOOD);
            worker.release();
            var prepared = preparation.get(5, TimeUnit.SECONDS);
            worker.pause();
            var attempt = prepared.dispatch(hooks::incrementAndGet);
            assertEquals(0, hooks.get());
            assertEquals(0, http.calls.get());
            if (cancel) {
                assertTrue(attempt.result().cancel(true));
            }
            worker.release();
            worker.marker().get(5, TimeUnit.SECONDS);
            if (cancel) {
                assertFalse(attempt.wasDispatched());
                assertEquals(0, hooks.get());
                assertEquals(0, http.calls.get());
            } else {
                assertEquals(GENERATED, attempt.result().get(5, TimeUnit.SECONDS));
                assertTrue(attempt.wasDispatched());
                assertEquals(1, hooks.get());
                assertEquals(1, http.calls.get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dispatch_closedOrRejectedBeforeSend_skipsHookAndHttp(boolean closeClient) throws Exception {
        ScriptedHttpClient http = http();
        AtomicInteger hooks = new AtomicInteger();
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http, worker)) {
            var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
            if (closeClient) {
                client.close();
            } else {
                worker.shutdown();
            }
            var attempt = prepared.dispatch(hooks::incrementAndGet);
            assertEquals(new LlmClient.Failed(closeClient ? LlmClient.Failure.CLOSED : LlmClient.Failure.UNAVAILABLE),
                    attempt.result().get(5, TimeUnit.SECONDS));
            assertFalse(attempt.wasDispatched());
            assertEquals(0, hooks.get());
            assertEquals(0, http.calls.get());
        }
    }

    @Test
    void dispatch_beforeSendHookThrows_preventsHttpAndLeavesAttemptUndispatched() throws Exception {
        ScriptedHttpClient http = http();
        AtomicInteger hooks = new AtomicInteger();
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
            var attempt = prepared.dispatch(() -> {
                hooks.incrementAndGet();
                throw new IllegalStateException("synthetic-private-hook-failure-canary");
            });
            assertEquals(new LlmClient.Failed(LlmClient.Failure.UNAVAILABLE),
                    attempt.result().get(5, TimeUnit.SECONDS));
            assertFalse(attempt.wasDispatched());
            assertEquals(1, hooks.get());
            assertEquals(0, http.calls.get());
            assertSafe(attempt.result().join().toString());
        }
    }

    @Test
    void dispatch_synchronousHttpFailureAfterHook_preservesChargedOwnership() throws Exception {
        ScriptedHttpClient http = http();
        AtomicInteger hooks = new AtomicInteger();
        http.beforeReturn = () -> {
            throw new IllegalStateException("synthetic-private-send-failure-canary");
        };
        try (HttpLlmClient client = client(configuration(ENDPOINT, MODEL), http,
                Executors.newVirtualThreadPerTaskExecutor())) {
            var prepared = client.prepare(TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);
            var attempt = prepared.dispatch(hooks::incrementAndGet);
            assertTrue(attempt.result().get(5, TimeUnit.SECONDS) instanceof LlmClient.Failed);
            assertTrue(attempt.wasDispatched());
            assertEquals(1, hooks.get());
            assertEquals(1, http.calls.get());
        }
    }

    private HttpLlmClient client(LlmConfiguration configuration, ScriptedHttpClient http, ExecutorService worker) {
        return new HttpLlmClient(configuration, http, worker, scheduler());
    }

    private LlmConfiguration configuration(URI endpoint, String model) throws IOException {
        Files.writeString(directory.resolve(".env"), "SOCLAAS_BASE_URL=" + endpoint + "\nSOCLAAS_API_KEY="
                + TOKEN + "\nSOCLAAS_MODEL=" + model + "\n");
        LlmConfiguration configuration = LlmConfiguration.load(directory);
        assertTrue(configuration.isAvailable());
        return configuration;
    }

    private String boundaryModel(int bytes) throws IOException {
        try (HttpLlmClient client = new HttpLlmClient(configuration(ENDPOINT, "m"))) {
            int base = client.buildRequest("Chair", Condition.GOOD).length;
            return "m" + "x".repeat(bytes - base - 3) + "\u4e2d";
        }
    }

    private static ScriptedHttpClient http() {
        ScriptedHttpClient http = new ScriptedHttpClient();
        http.chunks = List.of(RESPONSE.getBytes(StandardCharsets.UTF_8));
        return http;
    }

    private static HttpLlmClient.DeadlineScheduler scheduler() {
        return (callback, delay) -> () -> { };
    }

    private static void assertPreparationFailure(CompletableFuture<LlmClient.PreparedRequest> pending,
            LlmClient.Failure expected) {
        ExecutionException failure = assertThrows(ExecutionException.class, () -> pending.get(5, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof LlmClient.PreparationException);
        var preparation = (LlmClient.PreparationException) failure.getCause();
        assertEquals(expected, preparation.kind());
        assertNull(preparation.getCause());
        assertSafe(preparation.toString());
    }

    private static void assertSafe(String text) {
        for (String privateValue : List.of(TOKEN, MODEL, TITLE, ENDPOINT.toString(), "synthetic-provider")) {
            assertFalse(text.contains(privateValue));
        }
    }

    private static final class GatedExecutor extends AbstractExecutorService {
        private final ExecutorService delegate = Executors.newSingleThreadExecutor();
        private final List<Runnable> queued = new ArrayList<>();
        private boolean paused = true;
        private boolean shutdown;
        private volatile Thread lastThread;

        public synchronized void execute(Runnable action) {
            if (shutdown) {
                throw new RejectedExecutionException("Synthetic worker closed");
            }
            Runnable observed = () -> {
                lastThread = Thread.currentThread();
                action.run();
            };
            if (paused) {
                queued.add(observed);
            } else {
                delegate.execute(observed);
            }
        }

        synchronized void release() {
            paused = false;
            queued.forEach(delegate::execute);
            queued.clear();
        }

        synchronized void pause() {
            paused = true;
        }

        synchronized List<Runnable> snapshot() {
            return List.copyOf(queued);
        }

        CompletableFuture<Boolean> marker() {
            CompletableFuture<Boolean> marker = new CompletableFuture<>();
            execute(() -> marker.complete(true));
            return marker;
        }

        public synchronized void shutdown() {
            shutdown = true;
            delegate.shutdown();
        }

        public synchronized List<Runnable> shutdownNow() {
            shutdown = true;
            queued.clear();
            return delegate.shutdownNow();
        }

        public synchronized boolean isShutdown() {
            return shutdown;
        }

        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
