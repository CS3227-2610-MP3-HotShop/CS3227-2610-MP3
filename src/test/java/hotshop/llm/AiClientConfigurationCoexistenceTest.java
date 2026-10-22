package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import hotshop.model.Condition;

/** Exercises buyer and seller clients together with separate synthetic endpoints. */
class AiClientConfigurationCoexistenceTest {
    private static final String API_KEY = "synthetic-coexistence-token";
    private static final String MODEL = "synthetic-coexistence-model";
    private static final String BUYER_TEXT = "bicycle under S$200";
    private static final String SELLER_TITLE = "Wooden chair";
    private static final String DESCRIPTION = "A sturdy wooden chair in good condition.";

    private enum ClosingBorrower {
        BUYER,
        SELLER
    }

    @Test
    void clients_buyerAndSellerEndpointsConfigured_dispatchToTheirOwnProtocols() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            URI buyerBase = server.uri("/buyer/v1");
            URI sellerEndpoint = server.uri("/seller/responses?route=description");
            Map<String, String> settings = Map.of(
                    "SOCLAAS_BASE_URL", buyerBase.toString(),
                    "SOCLAAS_DESCRIPTION_URL", sellerEndpoint.toString(),
                    "SOCLAAS_API_KEY", API_KEY,
                    "SOCLAAS_MODEL", MODEL);
            LlmConfiguration sellerConfiguration = LlmConfiguration.loadFromEnvironment(null, () -> settings);
            assertTrue(sellerConfiguration.isAvailable());
            assertEquals(sellerEndpoint, sellerConfiguration.endpoint());

            String buyerResponse = chatCompletion("{\"titleText\":\"bicycle\",\"maxPriceCents\":20000}");
            String sellerResponse = "{\"status\":\"completed\",\"output_text\":"
                    + new Gson().toJson(DESCRIPTION) + "}";
            server.respondForRequests(request -> switch (request.uri().getPath()) {
                case "/buyer/v1/chat/completions" -> jsonResponse(buyerResponse);
                case "/seller/responses" -> jsonResponse(sellerResponse);
                default -> jsonResponse("{}");
            });

            try (var worker = Executors.newVirtualThreadPerTaskExecutor();
                    LlmHttpTransport transport = new LlmHttpTransport(server.trustedClient(), worker, noDeadline());
                    SocSearchClient buyer = new SocSearchClient(buyerBase, API_KEY, MODEL, transport);
                    HttpLlmClient seller = new HttpLlmClient(sellerConfiguration, transport)) {
                var buyerResult = buyer.propose(BUYER_TEXT).get(5, TimeUnit.SECONDS);
                buyer.close();
                var sellerResult = seller.generate(SELLER_TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS);

                var proposed = assertInstanceOf(SocSearchClient.Proposed.class, buyerResult);
                assertEquals("bicycle", proposed.search().titleText());
                assertEquals(Long.valueOf(20_000), proposed.search().maxPriceCents());
                assertEquals(new LlmClient.Generated(DESCRIPTION), sellerResult);

                ControlledHttpsServer.Request first = server.takeRequest();
                ControlledHttpsServer.Request second = server.takeRequest();
                Map<String, ControlledHttpsServer.Request> requests = Map.of(
                        first.uri().getPath(), first,
                        second.uri().getPath(), second);
                ControlledHttpsServer.Request buyerRequest = requests.get("/buyer/v1/chat/completions");
                ControlledHttpsServer.Request sellerRequest = requests.get("/seller/responses");
                assertTrue(buyerRequest != null);
                assertTrue(sellerRequest != null);
                assertEquals("route=description", sellerRequest.uri().getRawQuery());
                assertEquals("Bearer " + API_KEY, buyerRequest.authorization());
                assertEquals("Bearer " + API_KEY, sellerRequest.authorization());

                JsonObject buyerPayload = JsonParser.parseString(buyerRequest.bodyText()).getAsJsonObject();
                assertEquals(Set.of("model", "messages", "reasoning_effort"), buyerPayload.keySet());
                assertEquals(MODEL, buyerPayload.get("model").getAsString());
                JsonObject buyerMessage = buyerPayload.getAsJsonArray("messages").get(1).getAsJsonObject();
                JsonObject buyerInput = JsonParser.parseString(buyerMessage.get("content").getAsString())
                        .getAsJsonObject();
                assertEquals(BUYER_TEXT, buyerInput.get("buyerText").getAsString());
                assertFalse(buyerRequest.bodyText().contains(SELLER_TITLE));

                JsonObject sellerPayload = JsonParser.parseString(sellerRequest.bodyText()).getAsJsonObject();
                assertEquals(Set.of("model", "input"), sellerPayload.keySet());
                assertEquals(MODEL, sellerPayload.get("model").getAsString());
                String sellerInputText = sellerPayload.get("input").getAsString();
                JsonObject sellerInput = JsonParser.parseString(
                        sellerInputText.substring(sellerInputText.lastIndexOf('\n') + 1)).getAsJsonObject();
                assertEquals(SELLER_TITLE, sellerInput.get("title").getAsString());
                assertEquals(Condition.GOOD.name(), sellerInput.get("condition").getAsString());
                assertFalse(sellerInputText.contains(BUYER_TEXT));
                assertEquals(2, server.requestCount());
            }
        }
    }

    @Test
    void clients_sellerUnavailable_buyerStillUsesSharedTransport() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            URI buyerBase = server.uri("/buyer/v1");
            server.respond(200, "application/json", chatCompletion("{\"titleText\":\"bicycle\"}"));
            try (var worker = Executors.newVirtualThreadPerTaskExecutor();
                    LlmHttpTransport transport = new LlmHttpTransport(server.trustedClient(), worker, noDeadline());
                    HttpLlmClient seller = new HttpLlmClient(LlmConfiguration.load(null), transport);
                    SocSearchClient buyer = new SocSearchClient(buyerBase, API_KEY, MODEL, transport)) {
                assertEquals(new LlmClient.Failed(LlmClient.Failure.UNAVAILABLE),
                        seller.generate(SELLER_TITLE, Condition.GOOD).get(5, TimeUnit.SECONDS));
                var result = buyer.propose(BUYER_TEXT).get(5, TimeUnit.SECONDS);

                assertInstanceOf(SocSearchClient.Proposed.class, result);
                assertEquals("/buyer/v1/chat/completions", server.takeRequest().uri().getPath());
                assertEquals(1, server.requestCount());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ClosingBorrower.class)
    void close_pendingBorrowerRequest_otherClientCompletesSuccessfully(ClosingBorrower closingBorrower)
            throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            URI buyerBase = server.uri("/buyer/v1");
            URI sellerEndpoint = server.uri("/seller/responses");
            LlmConfiguration sellerConfiguration = LlmConfiguration.loadFromEnvironment(null, () -> Map.of(
                    "SOCLAAS_DESCRIPTION_URL", sellerEndpoint.toString(),
                    "SOCLAAS_API_KEY", API_KEY,
                    "SOCLAAS_MODEL", MODEL));
            var stall = new ControlledHttpsServer.StallGate(new CountDownLatch(2), new CountDownLatch(1));
            String buyerResponse = chatCompletion("{\"titleText\":\"bicycle\"}");
            String sellerResponse = "{\"status\":\"completed\",\"output_text\":"
                    + new Gson().toJson(DESCRIPTION) + "}";
            server.respondForRequests(request -> switch (request.uri().getPath()) {
                case "/buyer/v1/chat/completions" -> stalledJsonResponse(buyerResponse, stall);
                case "/seller/responses" -> stalledJsonResponse(sellerResponse, stall);
                default -> jsonResponse("{}");
            });

            try (var worker = Executors.newVirtualThreadPerTaskExecutor();
                    LlmHttpTransport transport = new LlmHttpTransport(server.trustedClient(), worker, noDeadline());
                    SocSearchClient buyer = new SocSearchClient(buyerBase, API_KEY, MODEL, transport);
                    HttpLlmClient seller = new HttpLlmClient(sellerConfiguration, transport)) {
                var buyerResult = buyer.propose(BUYER_TEXT);
                var sellerResult = seller.generate(SELLER_TITLE, Condition.GOOD);
                assertTrue(stall.started().await(5, TimeUnit.SECONDS));

                if (closingBorrower == ClosingBorrower.BUYER) {
                    buyer.close();
                    assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.CLOSED),
                            buyerResult.get(5, TimeUnit.SECONDS));
                } else {
                    seller.close();
                    assertEquals(new LlmClient.Failed(LlmClient.Failure.CLOSED),
                            sellerResult.get(5, TimeUnit.SECONDS));
                }
                stall.release().countDown();

                if (closingBorrower == ClosingBorrower.BUYER) {
                    assertEquals(new LlmClient.Generated(DESCRIPTION), sellerResult.get(5, TimeUnit.SECONDS));
                } else {
                    var proposed = assertInstanceOf(SocSearchClient.Proposed.class,
                            buyerResult.get(5, TimeUnit.SECONDS));
                    assertEquals("bicycle", proposed.search().titleText());
                }
                assertEquals(2, server.requestCount());
            } finally {
                stall.release().countDown();
            }
        }
    }

    @Test
    void sharedTransport_ownerClose_cancelsBuyerAndSellerExchanges() throws Exception {
        try (ControlledHttpsServer server = new ControlledHttpsServer()) {
            URI buyerBase = server.uri("/buyer/v1");
            URI sellerEndpoint = server.uri("/seller/responses");
            LlmConfiguration sellerConfiguration = LlmConfiguration.loadFromEnvironment(null, () -> Map.of(
                    "SOCLAAS_DESCRIPTION_URL", sellerEndpoint.toString(),
                    "SOCLAAS_API_KEY", API_KEY,
                    "SOCLAAS_MODEL", MODEL));
            var stall = new ControlledHttpsServer.StallGate(new CountDownLatch(2), new CountDownLatch(1));
            server.respondForRequests(request -> new ControlledHttpsServer.Response(200,
                    Map.of("Content-Type", "application/json"),
                    "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes(StandardCharsets.UTF_8), true, stall, false));

            try (var worker = Executors.newVirtualThreadPerTaskExecutor();
                    LlmHttpTransport transport = new LlmHttpTransport(server.trustedClient(), worker, noDeadline());
                    SocSearchClient buyer = new SocSearchClient(buyerBase, API_KEY, MODEL, transport);
                    HttpLlmClient seller = new HttpLlmClient(sellerConfiguration, transport)) {
                var buyerResult = buyer.propose(BUYER_TEXT);
                var sellerResult = seller.generate(SELLER_TITLE, Condition.GOOD);
                assertTrue(stall.started().await(5, TimeUnit.SECONDS));

                transport.close();

                assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.CLOSED),
                        buyerResult.get(5, TimeUnit.SECONDS));
                assertEquals(new LlmClient.Failed(LlmClient.Failure.CLOSED),
                        sellerResult.get(5, TimeUnit.SECONDS));
                assertEquals(2, server.requestCount());
            } finally {
                stall.release().countDown();
            }
        }
    }

    @Test
    void sharedTransport_oneCancellationAbortsPeer_ownerCloseReturnsClosedForBoth() throws Exception {
        ScriptedHttpClient http = new ScriptedHttpClient();
        http.stalled = true;
        http.cancelPeersWithIo = true;
        CountDownLatch dispatched = new CountDownLatch(2);
        http.beforeReturn = dispatched::countDown;

        URI buyerBase = URI.create("https://provider.invalid/buyer/v1");
        URI sellerEndpoint = URI.create("https://provider.invalid/seller/responses");
        LlmConfiguration sellerConfiguration = LlmConfiguration.loadFromEnvironment(null, () -> Map.of(
                "SOCLAAS_DESCRIPTION_URL", sellerEndpoint.toString(),
                "SOCLAAS_API_KEY", API_KEY,
                "SOCLAAS_MODEL", MODEL));

        try (var worker = Executors.newVirtualThreadPerTaskExecutor();
                LlmHttpTransport transport = new LlmHttpTransport(http, worker, noDeadline());
                SocSearchClient buyer = new SocSearchClient(buyerBase, API_KEY, MODEL, transport);
                HttpLlmClient seller = new HttpLlmClient(sellerConfiguration, transport)) {
            var buyerResult = buyer.propose(BUYER_TEXT);
            var sellerResult = seller.generate(SELLER_TITLE, Condition.GOOD);
            assertTrue(dispatched.await(5, TimeUnit.SECONDS));

            transport.close();

            assertEquals(new SocSearchClient.Failed(SocSearchClient.Failure.CLOSED),
                    buyerResult.get(5, TimeUnit.SECONDS));
            assertEquals(new LlmClient.Failed(LlmClient.Failure.CLOSED),
                    sellerResult.get(5, TimeUnit.SECONDS));
            assertEquals(2, http.calls.get());
        }
    }

    private static ControlledHttpsServer.Response jsonResponse(String body) {
        return new ControlledHttpsServer.Response(200, "application/json", body.getBytes(StandardCharsets.UTF_8),
                Map.of(), false);
    }

    private static ControlledHttpsServer.Response stalledJsonResponse(String body,
            ControlledHttpsServer.StallGate stall) {
        return new ControlledHttpsServer.Response(200, Map.of("Content-Type", "application/json"),
                body.getBytes(StandardCharsets.UTF_8), true, stall, false);
    }

    private static String chatCompletion(String output) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":" + new Gson().toJson(output) + "}}]}";
    }

    private static HttpLlmClient.DeadlineScheduler noDeadline() {
        return (callback, delay) -> () -> { };
    }
}
