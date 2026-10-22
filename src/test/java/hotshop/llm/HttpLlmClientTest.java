package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import hotshop.model.Condition;

class HttpLlmClientTest {
    private static final String TOKEN = "synthetic-transport-token";
    private static final String VALID = "{\"status\":\"completed\",\"output_text\":\"A chair in good condition.\"}";
    private static final LlmClient.Generated GENERATED = new LlmClient.Generated("A chair in good condition.");
    private static final URI ENDPOINT = URI.create("https://synthetic.invalid/exact?route=canary");

    @TempDir
    Path directory;

    @Test
    void createHttpClient_defaultPolicy_usesStrictTlsAndTenSecondConnectTimeout() {
        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
                HttpClient client = HttpLlmClient.createHttpClient(worker)) {
            assertEquals(Duration.ofSeconds(10), client.connectTimeout().orElseThrow());
            assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
            assertTrue(client.authenticator().isEmpty());
            assertTrue(client.cookieHandler().isEmpty());
        }
    }

    @Test
    void generate_loopbackHttps_postsExactUriAndOnlyExpectedHeadersAndBody() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            server.respond(200, "application/json; charset=utf-8", VALID);
            URI endpoint = server.uri("/private/exact?route=canary");
            try (HttpLlmClient client = client(endpoint, server.trustedClient(), new ManualDeadline())) {
                assertEquals(GENERATED, result(client));
                var captured = server.takeRequest();
                assertEquals("POST", captured.method());
                assertEquals("/private/exact?route=canary", captured.uri().toString());
                assertEquals("Bearer " + TOKEN, captured.authorization());
                assertEquals("application/json", header(captured, "Content-Type"));
                assertEquals("application/json", header(captured, "Accept"));
                assertEquals("identity", header(captured, "Accept-Encoding"));
                assertEquals(new String(client.buildRequest("Chair", Condition.GOOD), StandardCharsets.UTF_8),
                        captured.bodyText());
                assertFalse(captured.bodyText().contains(TOKEN));
                assertEquals(1, server.requestCount());
            }
        }
    }

    @Test
    void generate_redirect_doesNotContactDestination() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer();
                ControlledHttpsServer destination = new ControlledHttpsServer()) {
            server.redirect(destination.uri("/callback"));
            try (HttpLlmClient client = client(server.uri("/redirect"), server.trustedClient(), new ManualDeadline())) {
                assertEquals(new LlmClient.Failed(LlmClient.Failure.INVALID_RESPONSE), result(client));
                assertEquals(1, server.requestCount());
                assertEquals(0, destination.requestCount());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {65536, 65537})
    void generate_realChunkedBody_enforcesByteLimit(int size) throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            server.respondChunked(padded(size));
            try (HttpLlmClient client = client(server.uri("/chunked"), server.trustedClient(), new ManualDeadline())) {
                assertEquals(size == 65536 ? GENERATED : new LlmClient.Failed(LlmClient.Failure.INVALID_RESPONSE),
                        result(client));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"200,generated", "201,INVALID_RESPONSE", "202,INVALID_RESPONSE", "204,INVALID_RESPONSE",
        "301,INVALID_RESPONSE", "302,INVALID_RESPONSE", "307,INVALID_RESPONSE", "308,INVALID_RESPONSE",
        "400,UNAVAILABLE", "401,PROVIDER_AUTH", "403,PROVIDER_AUTH", "404,UNAVAILABLE", "418,UNAVAILABLE",
        "429,PROVIDER_BUSY", "500,PROVIDER_BUSY", "503,PROVIDER_BUSY", "599,PROVIDER_BUSY"})
    void generate_httpStatus_returnsSafeMappingWithoutRetry(int status, String expected) throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.status = status;
        http.headers = Map.of("Content-Type", List.of("application/json"),
                "Location", List.of("https://extra-destination.invalid/callback"),
                "Retry-After", List.of("synthetic-private-retry-canary"));
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(expected.equals("generated") ? GENERATED
                    : new LlmClient.Failed(LlmClient.Failure.valueOf(expected)), result(client));
            assertEquals(1, http.calls.get());
            assertEquals(ENDPOINT, http.request.uri());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/xml", "application/jsonp", ""})
    void generate_unsupportedContentType_rejectsResponse(String contentType) throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.headers = Map.of("Content-Type", List.of(contentType));
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
    }

    @Test
    void generate_noContentType_rejectsResponse() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.headers = Map.of();
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"synthetic-provider-body-canary", "{\"status\":\"failed\",\"output_text\":\"A chair\"}",
        "{\"status\":\"completed\",\"output_text\":\"\"}"})
    void generate_invalidEnvelope_returnsSafeInvalidResponse(String body) throws Exception {
        assertOutcome(scripted(body), LlmClient.Failure.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"br", "deflate", "gzip, identity"})
    void generate_unsupportedEncoding_rejectsResponse(String encoding) throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.headers = Map.of("Content-Type", List.of("application/json"), "Content-Encoding", List.of(encoding));
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
    }

    @Test
    void generate_multipleEncodingHeaders_rejectsAmbiguousResponse() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.headers = Map.of("Content-Type", List.of("application/json"),
                "Content-Encoding", List.of("gzip", "identity"));
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
    }

    @ParameterizedTest
    @CsvSource({"65536,false", "65537,false", "65536,true", "65537,true"})
    void generate_decodedByteBoundary_enforcesLimitWithoutLengthTrust(int size, boolean misleadingLength)
            throws Exception {
        ScriptedHttpClient http = scripted(padded(size));
        if (misleadingLength) {
            http.headers = Map.of("Content-Type", List.of("application/json"), "Content-Length", List.of("1"));
        }
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(size == 65536 ? GENERATED : new LlmClient.Failed(LlmClient.Failure.INVALID_RESPONSE),
                    result(client));
            if (size > 65536) {
                assertTrue(http.subscriptionCancelled.get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {65536, 65537})
    void generate_gzipDecodedByteBoundary_enforcesIndependentOutputLimit(int size) throws Exception {
        ScriptedHttpClient http = scriptedGzip(gzip(padded(size).getBytes(StandardCharsets.UTF_8)));
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(size == 65536 ? GENERATED : new LlmClient.Failed(LlmClient.Failure.INVALID_RESPONSE),
                    result(client));
        }
    }

    @Test
    void generate_gzipCompressedOverLimit_cancelsInput() throws Exception {
        byte[] compressed = gzip(VALID.getBytes(StandardCharsets.UTF_8));
        byte[] oversized = java.util.Arrays.copyOf(compressed, 65537);
        ScriptedHttpClient http = scriptedGzip(oversized);
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
        assertTrue(http.subscriptionCancelled.get());
    }

    @Test
    void generate_gzipCompressedExactly65536_acceptsBoundedOutput() throws Exception {
        byte[] compressed = gzip(VALID.getBytes(StandardCharsets.UTF_8));
        // RFC-compatible zero padding keeps the decoded JSON small while exercising the wire-byte boundary.
        ScriptedHttpClient http = scriptedGzip(java.util.Arrays.copyOf(compressed, 65536));
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(GENERATED, result(client));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void generate_concatenatedGzipMembers_rejectsExtraJsonAndDecodedOverflow(boolean coalesced, boolean oversized)
            throws Exception {
        byte[] first = gzip(VALID.getBytes(StandardCharsets.UTF_8));
        byte[] second = gzip((oversized ? "x".repeat(65537) : "{} A second document")
                .getBytes(StandardCharsets.UTF_8));
        ScriptedHttpClient http = scriptedGzip(first);
        if (coalesced) {
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            joined.write(first);
            joined.write(second);
            http.chunks = List.of(joined.toByteArray());
        } else {
            http.chunks = List.of(first, second);
        }
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
    }

    @Test
    void generate_jsonSpansGzipMembers_validatesCompleteDecodedDocument() throws Exception {
        int split = VALID.length() / 2;
        ScriptedHttpClient http = scriptedGzip(new byte[0]);
        http.chunks = List.of(gzip(VALID.substring(0, split).getBytes(StandardCharsets.UTF_8)),
                gzip(VALID.substring(split).getBytes(StandardCharsets.UTF_8)));
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(GENERATED, result(client));
        }
    }

    @Test
    void generate_corruptGzip_returnsOnlySafeFailure() throws Exception {
        assertOutcome(scriptedGzip("synthetic-provider-body-canary".getBytes(StandardCharsets.UTF_8)),
                LlmClient.Failure.INVALID_RESPONSE);
    }

    @Test
    void generate_networkFailures_mapsWithoutRawCauseOrRetry() throws Exception {
        for (Throwable failure : List.of(new UnknownHostException("synthetic-host-canary"),
                new IOException("synthetic-read-canary"),
                new javax.net.ssl.SSLHandshakeException("synthetic-certificate-canary"))) {
            ScriptedHttpClient http = scripted(VALID);
            http.failure = failure;
            assertOutcome(http, LlmClient.Failure.NETWORK);
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void generate_readFailureAfterPartialBody_returnsNetwork() throws Exception {
        ScriptedHttpClient http = scripted("{\"status\":");
        http.bodyFailure = new IOException("synthetic-provider-read-canary");
        assertOutcome(http, LlmClient.Failure.NETWORK);
        assertEquals(1, http.calls.get());
    }

    @Test
    void generate_stalledHeadersAtDeadline_cancelsHttpFuture() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.stalledHeaders = true;
        ManualDeadline deadline = new ManualDeadline();
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            var pending = client.generate("Chair", Condition.GOOD);
            http.awaitDispatch();
            assertFalse(pending.isDone());
            deadline.fire();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.get(5, TimeUnit.SECONDS));
            assertTrue(http.pendingExchange.isCancelled());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void generate_connectAndRequestTimeouts_returnsTimeout() throws Exception {
        for (Throwable failure : List.of(new HttpConnectTimeoutException("synthetic-connect-canary"),
                new HttpTimeoutException("synthetic-timeout-canary"))) {
            ScriptedHttpClient http = scripted(VALID);
            http.failure = failure;
            assertOutcome(http, LlmClient.Failure.TIMEOUT);
        }
    }

    @Test
    void generate_untrustedCertificate_returnsNetwork() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer();
                HttpLlmClient client = client(server.uri("/"), HttpClient.newHttpClient(), new ManualDeadline())) {
            assertEquals(new LlmClient.Failed(LlmClient.Failure.NETWORK), result(client));
            assertEquals(0, server.requestCount());
        }
    }

    @Test
    void generate_wrongHostname_returnsNetwork() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            URI endpoint = new URI("https", null, "127.0.0.1", server.uri("/").getPort(), "/", null, null);
            try (HttpLlmClient client = client(endpoint, server.trustedClient(), new ManualDeadline())) {
                assertEquals(new LlmClient.Failed(LlmClient.Failure.NETWORK), result(client));
                assertEquals(0, server.requestCount());
            }
        }
    }

    @Test
    void generate_stalledBodyAtDeadline_cancelsSubscriptionAndIgnoresLateBody() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.stalled = true;
        ManualDeadline deadline = new ManualDeadline();
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            CompletableFuture<LlmClient.Result> pending = client.generate("Chair", Condition.GOOD);
            http.awaitDispatch();
            assertFalse(pending.isDone());
            assertEquals(Duration.ofSeconds(45), deadline.delay);
            assertEquals(Duration.ofSeconds(45), http.request.timeout().orElseThrow());
            deadline.advance(Duration.ofSeconds(45).minusNanos(1));
            assertFalse(pending.isDone());
            deadline.advance(Duration.ofNanos(1));
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.get(5, TimeUnit.SECONDS));
            assertTrue(http.subscriptionCancelled.get());
            http.lateBody(VALID.getBytes(StandardCharsets.UTF_8));
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.join());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void generate_callerCancellation_releasesSubscriptionAndDeadline() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.stalled = true;
        ManualDeadline deadline = new ManualDeadline();
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            var pending = client.generate("Chair", Condition.GOOD);
            http.awaitDispatch();
            assertTrue(pending.cancel(true));
            assertTrue(http.subscriptionCancelled.get());
            assertTrue(deadline.cancelled.get());
            http.lateBody(VALID.getBytes(StandardCharsets.UTF_8));
            assertTrue(pending.isCancelled());
        }
    }

    @Test
    void close_pendingAndRepeatedClose_cancelsResourcesAndRejectsNewWork() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.stalled = true;
        ManualDeadline deadline = new ManualDeadline();
        ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
        HttpLlmClient client = new HttpLlmClient(configuration(ENDPOINT), http, worker, deadline);
        var pending = client.generate("Chair", Condition.GOOD);
        http.awaitDispatch();
        client.close();
        client.close();
        assertEquals(new LlmClient.Failed(LlmClient.Failure.CLOSED), pending.get(5, TimeUnit.SECONDS));
        assertEquals(new LlmClient.Failed(LlmClient.Failure.CLOSED), result(client));
        assertTrue(worker.isShutdown());
        assertTrue(deadline.closed.get());
        assertTrue(deadline.cancelled.get());
        assertTrue(http.subscriptionCancelled.get());
        assertEquals(1, http.calls.get());
    }

    @Test
    void generate_success_performsWorkOffCallerAndCancelsDeadline() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        ManualDeadline deadline = new ManualDeadline();
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            assertEquals(GENERATED, result(client));
            assertNotEquals(Thread.currentThread(), http.dispatchThread);
            assertTrue(deadline.cancelled.get());
        }
    }

    @Test
    void generate_networkFailure_cancelsDeadline() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.failure = new IOException("synthetic-network-canary");
        ManualDeadline deadline = new ManualDeadline();
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            assertEquals(new LlmClient.Failed(LlmClient.Failure.NETWORK), result(client));
            assertTrue(deadline.cancelled.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void generate_oversizeBufferList_cancelsBeforeAcceptingResponse(boolean combined) throws Exception {
        ScriptedHttpClient http = scripted(padded(65537));
        http.chunks = combined ? List.of(new byte[40000], new byte[25537]) : List.of(new byte[65537]);
        http.combinedBuffers = combined;
        assertOutcome(http, LlmClient.Failure.INVALID_RESPONSE);
        assertTrue(http.subscriptionCancelled.get());
    }

    @Test
    void generate_oneByteChunks_completesWithoutRecursiveDemandOverflow() throws Exception {
        ScriptedHttpClient http = scripted(padded(65536));
        List<byte[]> bytes = new ArrayList<>();
        for (byte value : padded(65536).getBytes(StandardCharsets.UTF_8)) {
            bytes.add(new byte[]{value});
        }
        http.chunks = bytes;
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(GENERATED, result(client));
        }
    }

    @ParameterizedTest
    @CsvSource({"false,5", "true,5", "false,20", "true,20"})
    void generate_partialGzipStall_timeoutOrCancellationReleasesInput(boolean cancel, int prefixBytes)
            throws Exception {
        byte[] compressed = gzip(VALID.getBytes(StandardCharsets.UTF_8));
        ScriptedHttpClient http = scriptedGzip(compressed);
        http.chunks = List.of(java.util.Arrays.copyOf(compressed, prefixBytes));
        http.stallAfterChunks = 1;
        // Stall after an incomplete header or compressed body rather than ending the stream.
        http.chunks = List.of(http.chunks.getFirst(), new byte[]{0});
        ManualDeadline deadline = new ManualDeadline();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (HttpLlmClient client = new HttpLlmClient(configuration(ENDPOINT), http, worker, deadline)) {
            var pending = client.generate("Chair", Condition.GOOD);
            http.awaitDispatch();
            if (cancel) {
                assertTrue(pending.cancel(true));
            } else {
                deadline.fire();
                assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.get(5, TimeUnit.SECONDS));
            }
            assertTrue(http.subscriptionCancelled.get());
            assertTrue(deadline.cancelled.get());
            assertTrue(worker.submit(() -> true).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void generate_deadlineBeforeFutureAssigned_cancelsReturnedHttpFuture() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.stalled = true;
        ManualDeadline deadline = new ManualDeadline();
        http.beforeReturn = () -> {
            try {
                deadline.fire();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        };
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            var pending = client.generate("Chair", Condition.GOOD);
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.get(5, TimeUnit.SECONDS));
            assertTrue(http.subscriptionCancelled.get());
            assertEquals(1, http.calls.get());
            assertTrue(http.futureCancelled.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void generate_subscriptionArrivesAfterDeadline_cancelsLateSubscription() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        http.deferSubscription = true;
        ManualDeadline deadline = new ManualDeadline();
        try (HttpLlmClient client = client(ENDPOINT, http, deadline)) {
            var pending = client.generate("Chair", Condition.GOOD);
            http.awaitDispatch();
            deadline.fire();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.get(5, TimeUnit.SECONDS));
            http.subscribeLate();
            assertTrue(http.subscriptionCancelled.get());
            assertEquals(1, http.calls.get());
        }
    }

    @Test
    void generate_fullBodyWithQueuedDecode_deadlineWinsBeforeParsing() throws Exception {
        ScriptedHttpClient http = scripted(VALID);
        ManualDeadline deadline = new ManualDeadline();
        QueuedExecutor worker = new QueuedExecutor();
        try (HttpLlmClient client = new HttpLlmClient(configuration(ENDPOINT), http, worker, deadline)) {
            var pending = client.generate("Chair", Condition.GOOD);
            for (int tasks = 0; !http.bodyDelivered && tasks < 10; tasks++) {
                worker.runNext();
            }
            assertTrue(http.bodyDelivered);
            assertFalse(pending.isDone());
            deadline.fire();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.get(5, TimeUnit.SECONDS));
            worker.drain();
            assertEquals(new LlmClient.Failed(LlmClient.Failure.TIMEOUT), pending.join());
        }
    }

    private void assertOutcome(ScriptedHttpClient http, LlmClient.Failure failure) throws Exception {
        try (HttpLlmClient client = client(ENDPOINT, http, new ManualDeadline())) {
            assertEquals(new LlmClient.Failed(failure), result(client));
        }
    }

    private HttpLlmClient client(URI endpoint, HttpClient http, ManualDeadline deadline) throws Exception {
        return new HttpLlmClient(configuration(endpoint), http, Executors.newVirtualThreadPerTaskExecutor(), deadline);
    }

    private LlmConfiguration configuration(URI endpoint) throws Exception {
        Files.writeString(directory.resolve(".env"), "SOCLAAS_DESCRIPTION_URL=" + endpoint + "\n"
                + "SOCLAAS_API_KEY=" + TOKEN + "\nSOCLAAS_MODEL=synthetic-model\n");
        LlmConfiguration configuration = LlmConfiguration.load(directory);
        assertTrue(configuration.isAvailable());
        return configuration;
    }

    private static LlmClient.Result result(HttpLlmClient client) throws Exception {
        return client.generate("Chair", Condition.GOOD).get(10, TimeUnit.SECONDS);
    }

    private static ScriptedHttpClient scripted(String body) {
        ScriptedHttpClient http = new ScriptedHttpClient();
        http.chunks = chunks(body.getBytes(StandardCharsets.UTF_8));
        return http;
    }

    private static ScriptedHttpClient scriptedGzip(byte[] body) {
        ScriptedHttpClient http = new ScriptedHttpClient();
        http.headers = Map.of("Content-Type", List.of("application/json"), "Content-Encoding", List.of("gzip"));
        http.chunks = chunks(body);
        return http;
    }

    private static List<byte[]> chunks(byte[] bytes) {
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += 997) {
            chunks.add(java.util.Arrays.copyOfRange(bytes, offset, Math.min(offset + 997, bytes.length)));
        }
        return chunks;
    }

    private static String padded(int bytes) {
        return VALID.substring(0, VALID.length() - 1) + ",\"metadata\":\""
                + "x".repeat(bytes - VALID.length() - 14) + "\"}";
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }

    private static String header(ControlledHttpsServer.Request request, String name) {
        return request.headers().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(entry -> entry.getValue().getFirst()).findFirst().orElseThrow();
    }

    private static final class ManualDeadline implements HttpLlmClient.DeadlineScheduler {
        private final CountDownLatch scheduled = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final FakeMonotonicClock clock = new FakeMonotonicClock();
        private final DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        private Duration delay;

        public HttpLlmClient.Cancellable schedule(Runnable callback, Duration delay) {
            this.delay = delay;
            var handle = scheduler.schedule(callback, delay);
            scheduled.countDown();
            return () -> {
                cancelled.set(true);
                handle.cancel();
            };
        }

        void fire() throws InterruptedException {
            assertTrue(scheduled.await(5, TimeUnit.SECONDS));
            advance(Duration.ofSeconds(45));
        }

        void advance(Duration elapsed) throws InterruptedException {
            assertTrue(scheduled.await(5, TimeUnit.SECONDS));
            clock.advance(elapsed);
            scheduler.runDue();
        }

        public void close() {
            closed.set(true);
        }
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
        private final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        private boolean shutdown;

        public void execute(Runnable action) {
            tasks.add(action);
        }

        void runNext() throws InterruptedException {
            Runnable action = tasks.poll(5, TimeUnit.SECONDS);
            assertTrue(action != null, "Expected queued worker task");
            action.run();
        }

        void drain() {
            Runnable action;
            while ((action = tasks.poll()) != null) {
                action.run();
            }
        }

        public void shutdown() {
            shutdown = true;
        }

        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(tasks);
            tasks.clear();
            return pending;
        }

        public boolean isShutdown() {
            return shutdown;
        }

        public boolean isTerminated() {
            return shutdown;
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }
}
