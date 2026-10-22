package hotshop.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

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

            try (SocSearchClient buyer = new SocSearchClient(buyerBase, API_KEY, MODEL,
                    server.trustedClient(), Duration.ofSeconds(5));
                    HttpLlmClient seller = new HttpLlmClient(sellerConfiguration, server.trustedClient(),
                            Executors.newVirtualThreadPerTaskExecutor(), noDeadline())) {
                var buyerResult = buyer.propose(BUYER_TEXT).get(5, TimeUnit.SECONDS);
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

    private static ControlledHttpsServer.Response jsonResponse(String body) {
        return new ControlledHttpsServer.Response(200, "application/json", body.getBytes(StandardCharsets.UTF_8),
                Map.of(), false);
    }

    private static String chatCompletion(String output) {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":" + new Gson().toJson(output) + "}}]}";
    }

    private static HttpLlmClient.DeadlineScheduler noDeadline() {
        return (callback, delay) -> () -> { };
    }
}
