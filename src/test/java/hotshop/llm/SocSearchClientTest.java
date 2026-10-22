package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.net.http.HttpClient.Version;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hotshop.service.ListingSort;

class SocSearchClientTest {
    private static final String TOKEN = "synthetic-token";
    private static final String MODEL = "synthetic-model";
    private static final String VALID_RESPONSE = envelope("{\"titleText\":\"books\"}");

    @Test
    void controlledHttpsServer_trustedClient_roundTripsChunkedResponse() throws Exception {
        try (var server = new ControlledHttpsServer(); var client = server.trustedClient()) {
            byte[] payload = "fixture response".getBytes(StandardCharsets.UTF_8);
            server.respondBytes(200, Map.of("Content-Type", "application/json"), payload, true);
            var request = HttpRequest.newBuilder(server.uri("/fixture")).GET().build();
            var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).get(5, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertArrayEquals(payload, response.body());
            var received = server.takeRequest();
            assertEquals("GET", received.method());
            assertEquals("/fixture", received.uri().getPath());
            assertEquals(0, received.body().length);
        }
    }

    @Test
    void controlledHttpsServer_stalledBody_releasesAfterFirstChunk() throws Exception {
        try (var server = new ControlledHttpsServer(); var client = server.trustedClient()) {
            byte[] payload = "first-second".getBytes(StandardCharsets.UTF_8);
            var gate = server.respondStalled(200, Map.of("Content-Type", "application/json"), payload);
            var request = HttpRequest.newBuilder(server.uri("/stall")).GET().build();
            var responseFuture = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            assertTrue(gate.started().await(5, TimeUnit.SECONDS));
            try (InputStream body = responseFuture.get(5, TimeUnit.SECONDS).body()) {
                assertEquals((int) 'f', body.read());
                gate.release().countDown();
                assertEquals("irst-second", new String(body.readAllBytes(), StandardCharsets.UTF_8));
            } finally {
                gate.release().countDown();
            }
        }
    }

    @Tag("security")
    @Test
    void constructor_endpointWithCredentials_rejectsConfigurationWithoutDetails() {
        assertInvalidConfiguration(URI.create("https://user:secret@example.invalid/v1"), TOKEN, MODEL);
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    void constructor_invalidConfiguration_rejectsWithFixedMessage(URI endpoint, String token, String model) {
        assertInvalidConfiguration(endpoint, token, model);
    }

    static Stream<Object[]> invalidConfigurations() {
        return Stream.of(
                new Object[] {URI.create("http://localhost/v1"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1#fragment"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1?x=1"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1/responses"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1/responses/"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1/chat/completions"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1/chat/completions/"), TOKEN, MODEL},
                new Object[] {URI.create("https:/path"), TOKEN, MODEL},
                new Object[] {URI.create("https://localhost/v1"), "bad\nvalue", MODEL},
                new Object[] {URI.create("https://localhost/v1"), "bad\rvalue", MODEL},
                new Object[] {URI.create("https://localhost/v1"), "  ", MODEL},
                new Object[] {URI.create("https://localhost/v1"), null, MODEL},
                new Object[] {URI.create("https://localhost/v1"), TOKEN, "  "});
    }

    @Tag("security")
    @Test
    void constructor_injectedClientAllowsRedirects_rejectsTransport() {
        var redirecting = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        assertThrows(RuntimeException.class, () -> new SocSearchClient(URI.create("https://localhost/v1"), TOKEN,
                MODEL, redirecting, Duration.ofSeconds(1)));
    }

    @Test
    void constructor_nonpositiveDeadline_rejectsConfiguration() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            assertThrows(RuntimeException.class, () -> new SocSearchClient(server.uri("/v1"), TOKEN, MODEL,
                    transport, Duration.ZERO));
        }
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("invalidBuyerTexts")
    void propose_invalidBuyerText_returnsInvalidInput(String buyerText) throws Exception {
        try (var client = new SocSearchClient(URI.create("https://localhost/v1"), TOKEN, MODEL)) {
            var failure = assertInstanceOf(SocSearchClient.Failed.class, await(client.propose(buyerText)));
            assertEquals(SocSearchClient.Failure.INVALID_INPUT, failure.kind());
        }
    }

    static Stream<String> invalidBuyerTexts() {
        return Stream.of(null, "", " \t\n ", "x".repeat(1001), "😀".repeat(1001));
    }

    @Test
    void propose_exactCodePointLimit_returnsValidatedProposal() throws Exception {
        assertProposal("😀".repeat(1000), VALID_RESPONSE, search -> assertEquals("books", search.titleText()));
    }

    @Test
    void propose_proposalFieldBoundaries_acceptsTitleAndPriceLimits() throws Exception {
        String title = "😀".repeat(120);
        String proposal = "{\"titleText\":" + quote(title)
                + ",\"category\":\"BOOKS\",\"conditions\":[\"NEW\",\"GOOD\"],"
                + "\"minPriceCents\":0,\"maxPriceCents\":100000000,\"sort\":\"PRICE_HIGH_TO_LOW\"}";
        assertProposal("query", envelope(proposal), search -> {
            assertEquals(title, search.titleText());
            assertEquals(hotshop.model.Category.BOOKS, search.category());
            assertEquals(java.util.Set.of(hotshop.model.Condition.NEW, hotshop.model.Condition.GOOD),
                    search.conditions());
            assertEquals(0L, search.minPriceCents());
            assertEquals(100_000_000L, search.maxPriceCents());
            assertEquals(ListingSort.PRICE_HIGH_TO_LOW, search.sort());
        });
    }

    @Test
    void propose_maxBuyerCodePointText_requestBodyStaysWithinByteLimit() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            server.respond(200, "application/json", VALID_RESPONSE);
            try (var client = client(server, transport)) {
                await(client.propose("x".repeat(1000)));
                assertTrue(server.takeRequest().body().length <= 8_192);
            }
        }
    }

    @Test
    void propose_requestBodyAtExactByteLimit_isDispatched() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            server.respond(200, "application/json", VALID_RESPONSE);
            byte[] baseline;
            try (var client = new SocSearchClient(server.uri("/v1"), TOKEN, "x", transport,
                    Duration.ofSeconds(5))) {
                assertInstanceOf(SocSearchClient.Proposed.class, await(client.propose("query")));
                baseline = server.takeRequest().body();
            }
            String boundaryModel = "x".repeat(1 + 8_192 - baseline.length);
            try (var client = new SocSearchClient(server.uri("/v1"), TOKEN, boundaryModel, transport,
                    Duration.ofSeconds(5))) {
                assertInstanceOf(SocSearchClient.Proposed.class, await(client.propose("query")));
                assertEquals(8_192, server.takeRequest().body().length);
            }
        }
    }

    @Test
    void propose_bodyDisconnect_mapsToNetworkFailure() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient();
                var client = client(server, transport)) {
            server.respondDisconnected(200, Map.of("Content-Type", "application/json"),
                    VALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
            var failure = assertInstanceOf(SocSearchClient.Failed.class, await(client.propose("query")));
            assertEquals(SocSearchClient.Failure.NETWORK, failure.kind());
        }
    }

    @Test
    void propose_deadlineBeforeTransportRegistration_cancelsReturnedOperation() throws Exception {
        try (var server = new ControlledHttpsServer()) {
            var gatedClient = new RegistrationGateClient();
            try (var client = new SocSearchClient(server.uri("/v1"), TOKEN, MODEL, gatedClient,
                    Duration.ofMillis(100))) {
                var invocation = CompletableFuture.supplyAsync(() -> client.propose("query"));
                assertTrue(gatedClient.entered.await(5, TimeUnit.SECONDS));
                Thread.sleep(150);
                gatedClient.release.countDown();
                var result = assertInstanceOf(SocSearchClient.Failed.class,
                        invocation.get(5, TimeUnit.SECONDS).get(5, TimeUnit.SECONDS));
                assertEquals(SocSearchClient.Failure.TIMEOUT, result.kind());
                assertTrue(gatedClient.operationCancelled.get());
            }
        }
    }

    @Test
    void propose_closeDuringDeadlineRegistration_returnsClosedWithoutDispatch() throws Exception {
        try (var server = new ControlledHttpsServer()) {
            var scheduler = new GatedScheduler();
            try (var client = new SocSearchClient(server.uri("/v1"), TOKEN, MODEL,
                    new RegistrationGateClient(), Duration.ofSeconds(5), scheduler)) {
                var invocation = CompletableFuture.supplyAsync(() -> client.propose("query"));
                assertTrue(scheduler.entered.await(5, TimeUnit.SECONDS));
                client.close();
                scheduler.release.countDown();
                var pending = invocation.get(5, TimeUnit.SECONDS);
                assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.CLOSED),
                        pending.get(5, TimeUnit.SECONDS));
                assertTrue(scheduler.awaitTermination(5, TimeUnit.SECONDS));
                assertTrue(scheduler.getQueue().isEmpty());
                assertFalse(server.hasRequest(200, TimeUnit.MILLISECONDS));
            } finally {
                scheduler.release.countDown();
                scheduler.shutdownNow();
            }
        }
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("providerFailures")
    void propose_providerStatus_mapsToSafeCategory(int status, SocSearchClient.Failure expected) throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            server.respond(status, "application/json", "provider body may contain a secret-looking value");
            try (var client = client(server, transport)) {
                var failure = assertInstanceOf(SocSearchClient.Failed.class, await(client.propose("books")));
                assertEquals(expected, failure.kind());
                assertEquals("Bearer " + TOKEN, server.takeRequest().header("Authorization"));
                assertFalse(failure.toString().contains(TOKEN));
                assertFalse(failure.toString().contains("provider body"));
                assertNoAdditionalRequest(server);
            }
        }
    }

    static Stream<Object[]> providerFailures() {
        return Stream.of(
                new Object[] {401, SocSearchClient.Failure.PROVIDER_AUTH},
                new Object[] {403, SocSearchClient.Failure.PROVIDER_AUTH},
                new Object[] {429, SocSearchClient.Failure.PROVIDER_BUSY},
                new Object[] {500, SocSearchClient.Failure.PROVIDER_BUSY},
                new Object[] {503, SocSearchClient.Failure.PROVIDER_BUSY},
                new Object[] {400, SocSearchClient.Failure.INVALID_RESPONSE},
                new Object[] {302, SocSearchClient.Failure.INVALID_RESPONSE});
    }

    @Test
    void propose_success_postsExactChatContractAndBuyerData() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            server.respond(200, "application/json", VALID_RESPONSE);
            try (var client = new SocSearchClient(server.uri("/v1/"), TOKEN, MODEL, transport,
                    Duration.ofSeconds(5))) {
                var proposed = assertInstanceOf(SocSearchClient.Proposed.class, await(client.propose("  books  ")));
                assertEquals("books", proposed.search().titleText());
                assertEquals(null, proposed.search().category());
                assertEquals(java.util.Set.of(), proposed.search().conditions());
                assertEquals(null, proposed.search().minPriceCents());
                assertEquals(null, proposed.search().maxPriceCents());
                assertEquals(ListingSort.NEWEST, proposed.search().sort());
                var request = server.takeRequest();
                assertEquals("POST", request.method());
                assertEquals("/v1/chat/completions", request.uri().getPath());
                assertEquals("Bearer " + TOKEN, request.header("Authorization"));
                assertEquals("application/json", request.header("Content-Type"));
                assertEquals("application/json", request.header("Accept"));
                assertEquals("identity", request.header("Accept-Encoding"));
                JsonObject json = JsonParser.parseString(new String(request.body(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertEquals(java.util.Set.of("model", "messages", "reasoning_effort"), json.keySet());
                assertEquals(MODEL, json.get("model").getAsString());
                assertEquals("none", json.get("reasoning_effort").getAsString());
                var messages = json.getAsJsonArray("messages");
                assertEquals(2, messages.size());
                assertEquals(java.util.Set.of("role", "content"), messages.get(0).getAsJsonObject().keySet());
                assertEquals(java.util.Set.of("role", "content"), messages.get(1).getAsJsonObject().keySet());
                assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
                assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
                assertEquals("books", JsonParser.parseString(messages.get(1).getAsJsonObject()
                        .get("content").getAsString()).getAsJsonObject().get("buyerText").getAsString());
            }
        }
    }

    @Tag("security")
    @Test
    void propose_buyerTextContainingJsonSyntax_remainsUntrustedStringData() throws Exception {
        String buyerText = "\"},\"model\":\"attacker\" #\\n";
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient();
                var client = client(server, transport)) {
            server.respond(200, "application/json", VALID_RESPONSE);
            await(client.propose(buyerText));
            JsonObject request = capturedJson(server);
            assertEquals(java.util.Set.of("model", "messages", "reasoning_effort"), request.keySet());
            assertEquals(MODEL, request.get("model").getAsString());
            assertEquals(buyerText, buyerData(request.getAsJsonArray("messages").get(1).getAsJsonObject()
                    .get("content").getAsString()).get("buyerText").getAsString());
        }
    }

    @Test
    void propose_buyerTextInteriorWhitespace_preservesTextAndStripsOutside() throws Exception {
        String buyerText = "  find  the   book  ";
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient();
                var client = client(server, transport)) {
            server.respond(200, "application/json", VALID_RESPONSE);
            await(client.propose(buyerText));
            String input = capturedJson(server).getAsJsonArray("messages").get(1).getAsJsonObject()
                    .get("content").getAsString();
            assertEquals("find  the   book", buyerData(input).get("buyerText").getAsString());
        }
    }

    @Tag("security")
    @Test
    void propose_redirectResponse_doesNotForwardCredentialsOrRetry() throws Exception {
        try (var source = new ControlledHttpsServer(); var destination = new ControlledHttpsServer();
                var transport = source.trustedClient()) {
            source.respondBytes(302, Map.of("Location", destination.uri("/stolen").toString()), new byte[0], false);
            try (var client = client(source, transport)) {
                var result = assertInstanceOf(SocSearchClient.Failed.class, await(client.propose("query")));
                assertEquals(SocSearchClient.Failure.INVALID_RESPONSE, result.kind());
                assertEquals("Bearer " + TOKEN, source.takeRequest().header("Authorization"));
                assertFalse(destination.hasRequest(200, TimeUnit.MILLISECONDS));
                assertNoAdditionalRequest(source);
            }
        }
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("invalidEnvelopes")
    void propose_malformedEnvelope_returnsInvalidResponse(String response) throws Exception {
        assertFailureResponse(response.getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/json"));
    }

    static Stream<String> invalidEnvelopes() {
        return Stream.of(
                "not json",
                "{} trailing",
                "{/*comment*/\"choices\":[]}",
                "{\"choices\":[],\"choices\":[]}",
                "{\"choices\":[]}",
                "{\"choices\":[3]}",
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"user\","
                        + "\"content\":\"{}\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"  \"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"{}\",\"refusal\":\"refused\"}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"{}\",\"function_call\":{}}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"{}\",\"tool_calls\":[{}]}}]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"{}\"}},{\"finish_reason\":\"length\","
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}]}",
                envelopeWithMetadata(nestedObject(33)));
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("invalidProposals")
    void propose_invalidProposalFields_returnsInvalidResponse(String proposal) throws Exception {
        assertFailureResponse(envelope(proposal).getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/json"));
    }

    static Stream<String> invalidProposals() {
        return Stream.of(
                "[]", "{\"unknown\":1}", "{\"titleText\":1}",
                "{\"category\":\"NOT_A_CATEGORY\"}", "{\"conditions\":[\"UNKNOWN\"]}",
                "{\"minPriceCents\":-1}", "{\"maxPriceCents\":100000001}",
                "{\"minPriceCents\":3,\"maxPriceCents\":2}",
                "{\"minPriceCents\":1.0}", "{\"minPriceCents\":1e0}",
                "{\"titleText\":\"" + "x".repeat(121) + "\"}");
    }

    @Test
    void propose_firstChoiceContent_isAuthoritative() throws Exception {
        String response = "{\"choices\":["
                + "{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{\\\"titleText\\\":\\\"first\\\"}\"}},"
                + "{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{\\\"titleText\\\":\\\"second\\\"}\"}}]}";
        assertProposal("query", response, search -> assertEquals("first", search.titleText()));
    }

    @Test
    void propose_nullRefusalFunctionAndToolFields_acceptsAssistantContent() throws Exception {
        for (String tools : new String[] {"null", "[]"}) {
            String response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{"
                    + "\"role\":\"assistant\",\"content\":\"{\\\"titleText\\\":\\\"books\\\"}\","
                    + "\"refusal\":null,\"function_call\":null,\"tool_calls\":" + tools + "}}]}";
            assertProposal("query", response, search -> assertEquals("books", search.titleText()));
        }
    }

    @Test
    void propose_envelopeDepthBoundary_acceptsThirtyTwoContainers() throws Exception {
        assertProposal("query", nestedObject(31), search -> assertEquals(null, search.titleText()));
    }

    @Tag("security")
    @ParameterizedTest
    @MethodSource("invalidCompletionContent")
    void propose_invalidCompletionContent_returnsInvalidResponse(String content) throws Exception {
        assertFailureResponse(envelope(content).getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/json"));
    }

    static Stream<String> invalidCompletionContent() {
        return Stream.of("[]", "{} trailing", "{\"titleText\":1,\"titleText\":2}", nestedObject(33),
                "```json\\n{}\\n```");
    }

    @Tag("security")
    @Test
    void propose_malformedUtf8_returnsInvalidResponse() throws Exception {
        byte[] invalid = new byte[] {'{', '"', 's', 't', 'a', 't', 'u', 's', '"', ':', '"', 'c', 'o', 'm', 'p', 'l',
                'e', 't', 'e', 'd', '"', ',', '"', 'o', 'u', 't', 'p', 'u', 't', '_', 't', 'e', 'x', 't', '"', ':', '"',
                (byte) 0xc3, '"', '}'};
        assertFailureResponse(invalid, Map.of("Content-Type", "application/json"));
    }

    @Tag("security")
    @Test
    void propose_unsupportedContentEncoding_returnsInvalidResponse() throws Exception {
        assertFailureResponse(VALID_RESPONSE.getBytes(StandardCharsets.UTF_8),
                Map.of("Content-Type", "application/json", "Content-Encoding", "gzip"));
    }

    @Test
    void propose_identityContentEncoding_acceptsResponse() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient();
                var client = client(server, transport)) {
            server.respondBytes(200, Map.of("Content-Type", "application/json", "Content-Encoding", "identity"),
                    VALID_RESPONSE.getBytes(StandardCharsets.UTF_8), false);
            var result = assertInstanceOf(SocSearchClient.Proposed.class, await(client.propose("query")));
            assertEquals("books", result.search().titleText());
        }
    }

    @Test
    void propose_maxResponseBytes_acceptsExactLimit() throws Exception {
        byte[] body = paddedValidEnvelope(65_536);
        assertEquals(65_536, body.length);
        assertProposal("query", body, search -> assertEquals("books", search.titleText()), true);
    }

    @Test
    void propose_requestBodyTooLarge_returnsInvalidInputBeforeDispatch() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient();
                var client = new SocSearchClient(server.uri("/v1"), TOKEN, "x".repeat(8192), transport,
                        Duration.ofSeconds(5))) {
            var failure = assertInstanceOf(SocSearchClient.Failed.class, await(client.propose("query")));
            assertEquals(SocSearchClient.Failure.INVALID_INPUT, failure.kind());
            assertFalse(server.hasRequest(150, TimeUnit.MILLISECONDS));
        }
    }

    @Tag("security")
    @Tag("security")
    @Test
    void propose_responseExceedsByteLimit_returnsInvalidResponse() throws Exception {
        byte[] body = paddedValidEnvelope(65_537);
        assertEquals(65_537, body.length);
        assertFailureResponse(body, Map.of("Content-Type", "application/json"), true);
    }

    @Tag("security")
    @Test
    void propose_stalledResponseBody_hitsTotalDeadline() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            var gate = server.respondStalled(200, Map.of("Content-Type", "application/json"),
                    VALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
            try (var client = new SocSearchClient(server.uri("/v1"), TOKEN, MODEL, transport,
                    Duration.ofMillis(100))) {
                var result = client.propose("query").get(2, TimeUnit.SECONDS);
                assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.TIMEOUT), result);
                assertTrue(gate.started().await(5, TimeUnit.SECONDS));
            } finally {
                gate.release().countDown();
            }
        }
    }

    @Tag("security")
    @Test
    void close_pendingRequest_cancelsAndCompletesClosed() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            var gate = server.respondStalled(200, Map.of("Content-Type", "application/json"),
                    VALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
            var client = new SocSearchClient(server.uri("/v1"), TOKEN, MODEL, transport, Duration.ofSeconds(10));
            var pending = client.propose("query");
            assertTrue(gate.started().await(5, TimeUnit.SECONDS));
            client.close();
            assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.CLOSED), pending.get(2, TimeUnit.SECONDS));
            gate.release().countDown();
            assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.CLOSED), await(pending));
        }
    }

    @Test
    void propose_concurrentInputs_remainIsolated() throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient();
                var client = client(server, transport)) {
            server.respondForRequests(request -> {
                String requestBody = new String(request.body(), StandardCharsets.UTF_8);
                String title = requestBody.contains("first") ? "first" : "second";
                byte[] body = envelope("{\"titleText\":" + quote(title) + "}").getBytes(StandardCharsets.UTF_8);
                return new ControlledHttpsServer.Response(200, Map.of("Content-Type", "application/json"), body,
                        false, null, false);
            });
            var first = client.propose("first");
            var second = client.propose("second");
            var firstResult = assertInstanceOf(SocSearchClient.Proposed.class, await(first));
            var secondResult = assertInstanceOf(SocSearchClient.Proposed.class, await(second));
            assertEquals("first", firstResult.search().titleText());
            assertEquals("second", secondResult.search().titleText());
            var requestOne = server.takeRequest();
            var requestTwo = server.takeRequest();
            String inputOne = JsonParser.parseString(new String(requestOne.body(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("messages").get(1).getAsJsonObject()
                    .get("content").getAsString();
            String inputTwo = JsonParser.parseString(new String(requestTwo.body(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("messages").get(1).getAsJsonObject()
                    .get("content").getAsString();
            String buyerOne = buyerData(inputOne).get("buyerText").getAsString();
            String buyerTwo = buyerData(inputTwo).get("buyerText").getAsString();
            assertTrue("first".equals(buyerOne) && "second".equals(buyerTwo)
                    || "second".equals(buyerOne) && "first".equals(buyerTwo));
        }
    }

    private static void assertInvalidConfiguration(URI endpoint, String token, String model) {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new SocSearchClient(endpoint, token, model));
        assertEquals("Invalid SoC configuration", exception.getMessage());
        assertEquals(null, exception.getCause());
    }

    private static void assertFailureResponse(byte[] body, Map<String, String> headers) throws Exception {
        assertFailureResponse(body, headers, false);
    }

    private static void assertFailureResponse(byte[] body, Map<String, String> headers, boolean chunked)
            throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            server.respondBytes(200, headers, body, chunked);
            try (var client = client(server, transport)) {
                var result = assertInstanceOf(SocSearchClient.Failed.class, await(client.propose("query")));
                assertEquals(SocSearchClient.Failure.INVALID_RESPONSE, result.kind());
            }
        }
    }

    private static void assertProposal(String buyerText, String response,
            java.util.function.Consumer<hotshop.service.ListingSearch> assertion) throws Exception {
        assertProposal(buyerText, response.getBytes(StandardCharsets.UTF_8), assertion, false);
    }

    private static void assertProposal(String buyerText, byte[] body,
            java.util.function.Consumer<hotshop.service.ListingSearch> assertion, boolean chunked) throws Exception {
        try (var server = new ControlledHttpsServer(); var transport = server.trustedClient()) {
            server.respondBytes(200, Map.of("Content-Type", "application/json"), body, chunked);
            try (var client = client(server, transport)) {
                var proposal = assertInstanceOf(SocSearchClient.Proposed.class, await(client.propose(buyerText)));
                assertion.accept(proposal.search());
            }
        }
    }

    private static SocSearchClient client(ControlledHttpsServer server, java.net.http.HttpClient transport) {
        return new SocSearchClient(server.uri("/v1"), TOKEN, MODEL, transport, Duration.ofSeconds(5));
    }

    private static void assertNoAdditionalRequest(ControlledHttpsServer server) throws InterruptedException {
        assertFalse(server.hasRequest(150, TimeUnit.MILLISECONDS));
    }

    private static JsonObject capturedJson(ControlledHttpsServer server) throws InterruptedException {
        var request = server.takeRequest();
        return JsonParser.parseString(new String(request.body(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject buyerData(String input) {
        return JsonParser.parseString(input).getAsJsonObject();
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static String envelope(String outputText) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":" + quote(outputText) + "}}]}";
    }

    private static String quote(String value) {
        return new com.google.gson.Gson().toJson(value);
    }

    private static final class RegistrationGateClient extends HttpClient {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean operationCancelled = new AtomicBoolean();

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                BodyHandler<T> responseBodyHandler) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Transport registration gate timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return new CompletableFuture<>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    operationCancelled.set(true);
                    return super.cancel(mayInterruptIfRunning);
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                BodyHandler<T> responseBodyHandler, PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private static final class GatedScheduler extends ScheduledThreadPoolExecutor {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private GatedScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Deadline registration gate timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Deadline registration gate interrupted", exception);
            }
            return super.schedule(command, delay, unit);
        }
    }

    private static String nestedObject(int levels) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{}\"}}],\"metadata\":"
                + "[".repeat(levels) + "0" + "]".repeat(levels) + "}";
    }

    private static String envelopeWithMetadata(String nestedMetadata) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{}\"}}],\"metadata\":" + nestedMetadata + "}";
    }

    private static byte[] paddedValidEnvelope(int targetLength) {
        String prefix = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{\\\"titleText\\\":\\\"books\\\"}\"}}],\"metadata\":\"";
        String suffix = "\"}";
        int paddingLength = targetLength - prefix.getBytes(StandardCharsets.UTF_8).length
                - suffix.getBytes(StandardCharsets.UTF_8).length;
        return (prefix + "x".repeat(paddingLength) + suffix).getBytes(StandardCharsets.UTF_8);
    }
}
