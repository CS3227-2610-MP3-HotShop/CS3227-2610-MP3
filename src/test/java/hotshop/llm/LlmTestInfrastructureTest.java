package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Test;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import hotshop.model.Condition;

/** Tests the fixtures themselves, independently of future generation implementation. */
class LlmTestInfrastructureTest {
    @Test
    void streamJson_strictGson_preservesUnicodeAndRejectsComments() throws Exception {
        StringWriter encoded = new StringWriter();
        try (JsonWriter writer = new JsonWriter(encoded)) {
            writer.setStrictness(Strictness.STRICT);
            writer.beginObject().name("title").value("café \"book\"").endObject();
        }
        try (JsonReader reader = new JsonReader(new StringReader(encoded.toString()))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginObject();
            assertEquals("title", reader.nextName());
            assertEquals("café \"book\"", reader.nextString());
            reader.endObject();
        }
        try (JsonReader reader = new JsonReader(new StringReader("/* comment */ {}"))) {
            reader.setStrictness(Strictness.STRICT);
            assertThrows(java.io.IOException.class, reader::beginObject);
        }
    }

    @Test
    void request_controlledHttps_capturesSyntheticHeaderAndUtf8Body() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer();
                HttpClient client = server.trustedClient()) {
            server.respond(200, "application/json", "{\"status\":\"completed\"}");
            var request = HttpRequest.newBuilder(server.uri("/exact?item=1"))
                    .header("Authorization", "Bearer synthetic-token")
                    .POST(HttpRequest.BodyPublishers.ofString("synthetic café"))
                    .timeout(Duration.ofSeconds(5)).build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(10, TimeUnit.SECONDS);
            var captured = server.takeRequest();
            assertEquals(200, response.statusCode());
            assertEquals("{\"status\":\"completed\"}", response.body());
            assertEquals("/exact?item=1", captured.uri().toString());
            assertEquals("POST", captured.method());
            assertEquals("Bearer synthetic-token", captured.authorization());
            assertEquals("synthetic café", captured.body());
        }
    }

    @Test
    void request_untrustedTestCertificate_failsHandshake() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer();
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertHandshakeFailure(client, server.uri("/"));
        }
    }

    @Test
    void request_wrongHostname_failsHandshake() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer();
                HttpClient client = server.trustedClient()) {
            URI endpoint = server.uri("/");
            assertHandshakeFailure(client, new URI("https", null, "127.0.0.1", endpoint.getPort(), "/", null, null));
        }
    }

    @Test
    void generate_capturingClient_remainsPendingUntilExplicitCompletion() throws Exception {
        CapturingLlmClient client = new CapturingLlmClient();
        var result = client.generate("System design textbook", Condition.GOOD);
        var call = client.takeCall();
        assertEquals("System design textbook", call.title());
        assertEquals(Condition.GOOD, call.condition());
        assertFalse(result.isDone());
        call.completion().complete(new LlmClient.Generated("A textbook in good condition."));
        assertEquals(new LlmClient.Generated("A textbook in good condition."), result.get(1, TimeUnit.SECONDS));
        assertEquals(1, client.callCount());
    }

    @Test
    void advance_exactNanosecondBoundary_changesOnlyMonotonicTime() {
        FakeMonotonicClock clock = new FakeMonotonicClock();
        clock.advance(Duration.ofNanos(59_999_999_999L));
        assertEquals(59_999_999_999L, clock.getAsLong());
        clock.advance(Duration.ofNanos(1));
        assertEquals(60_000_000_000L, clock.getAsLong());
        assertThrows(IllegalArgumentException.class, () -> clock.advance(Duration.ofNanos(-1)));
        assertEquals(60_000_000_000L, clock.getAsLong());
    }

    @Test
    void advance_scheduledAndCancelledTasks_runsInDeadlineOrderOnce() {
        FakeMonotonicClock clock = new FakeMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        List<Integer> completed = new ArrayList<>();
        scheduler.schedule(() -> completed.add(2), Duration.ofSeconds(2));
        scheduler.schedule(() -> completed.add(1), Duration.ofSeconds(1));
        var cancelled = scheduler.schedule(() -> completed.add(9), Duration.ofSeconds(1));
        assertTrue(cancelled.cancel());
        clock.advance(Duration.ofNanos(999_999_999));
        scheduler.runDue();
        assertTrue(completed.isEmpty());
        clock.advance(Duration.ofNanos(1));
        scheduler.runDue();
        assertEquals(List.of(1), completed);
        clock.advance(Duration.ofSeconds(1));
        scheduler.runDue();
        scheduler.runDue();
        assertEquals(List.of(1, 2), completed);
    }

    @Test
    void release_completionGate_dispatchesThroughChosenUiExecutorOnce() {
        ArrayDeque<Runnable> uiQueue = new ArrayDeque<>();
        CompletionGate gate = new CompletionGate(uiQueue::add);
        List<String> delivered = new ArrayList<>();
        gate.hold(() -> delivered.add("delivered"));
        assertTrue(uiQueue.isEmpty());
        gate.release();
        assertTrue(delivered.isEmpty());
        assertEquals(1, uiQueue.size());
        uiQueue.remove().run();
        gate.release();
        assertEquals(List.of("delivered"), delivered);
        assertTrue(uiQueue.isEmpty());
    }

    private static void assertHandshakeFailure(HttpClient client, URI endpoint) {
        var future = client.sendAsync(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof SSLHandshakeException);
    }
}
