package hotshop.llm;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.Strictness;
import hotshop.service.AiSearchProposalValidator;
import hotshop.service.ListingSearch;

/** Makes bounded SoC requests and converts completed responses to validated buyer search proposals. */
public final class SocSearchClient implements AutoCloseable {
    private static final String INVALID_CONFIGURATION = "Invalid SoC configuration";
    private static final String INSTRUCTIONS = "Interpret the buyer's request as listing search filters. "
            + "Return only one JSON object using these optional keys: titleText (string), category "
            + "(one of BOOKS, CLOTHING, ELECTRONICS, FURNITURE, OTHER, SPORTS), conditions "
            + "(array of NEW, LIKE_NEW, GOOD, FAIR, POOR), minPriceCents and maxPriceCents "
            + "(nonnegative integer SGD cents), sort (one of NEWEST, PRICE_LOW_TO_HIGH, "
            + "PRICE_HIGH_TO_LOW). Do not include actions, identity, status, or extra keys.";
    private static final int MAX_REQUEST_BYTES = 8_192;
    private static final int MAX_RESPONSE_BYTES = BoundedLlmBodySubscriber.MAX_BODY_BYTES;
    private static final int MAX_DEPTH = 32;
    private static final Gson GSON = new Gson();

    /** Result of a buyer search proposal request. */
    public sealed interface Result permits Proposed, Failed {
    }

    /** A validated listing search proposal. */
    public record Proposed(ListingSearch search) implements Result {
    }

    /** A safe public failure category. */
    public record Failed(Failure kind) implements Result {
    }

    /** Failure categories exposed by the client. */
    public enum Failure {
        INVALID_INPUT,
        TIMEOUT,
        PROVIDER_AUTH,
        PROVIDER_BUSY,
        NETWORK,
        INVALID_RESPONSE,
        CLOSED
    }

    private final URI endpoint;
    private final String bearerToken;
    private final String model;
    private final LlmHttpTransport transport;
    private final boolean ownsTransport;
    private final Duration deadline;
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Creates a client using the standard HTTP transport. */
    public SocSearchClient(URI endpoint, String bearerToken, String model) {
        this(endpoint, bearerToken, model, new LlmHttpTransport(), Duration.ofSeconds(45), true);
    }

    /** Borrows the process-owned HTTP resources; close cancels only this client's requests. */
    public SocSearchClient(URI endpoint, String bearerToken, String model, LlmHttpTransport transport) {
        this(endpoint, bearerToken, model, transport, Duration.ofSeconds(45), false);
    }

    SocSearchClient(URI endpoint, String bearerToken, String model, HttpClient client, Duration deadline) {
        this(endpoint, bearerToken, model, client, deadline, createTimer());
    }

    SocSearchClient(URI endpoint, String bearerToken, String model, HttpClient client, Duration deadline,
            ScheduledExecutorService timer) {
        this(endpoint, bearerToken, model, createTransport(client, timer), deadline, true);
    }

    private SocSearchClient(URI endpoint, String bearerToken, String model, LlmHttpTransport transport,
            Duration deadline, boolean ownsTransport) {
        if (!validEndpoint(endpoint) || !validToken(bearerToken) || model == null || model.isBlank()
                || transport == null
                || deadline == null || deadline.isZero() || deadline.isNegative()) {
            throw new IllegalArgumentException(INVALID_CONFIGURATION);
        }
        this.endpoint = chatCompletionsEndpoint(endpoint);
        this.bearerToken = bearerToken;
        this.model = model;
        this.transport = transport;
        this.ownsTransport = ownsTransport;
        this.deadline = deadline;
    }

    private static ScheduledExecutorService createTimer() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "soc-search-deadline");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static LlmHttpTransport createTransport(HttpClient client, ScheduledExecutorService timer) {
        if (client == null || client.followRedirects() != HttpClient.Redirect.NEVER || timer == null) {
            throw new IllegalArgumentException(INVALID_CONFIGURATION);
        }
        ExecutorService worker = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "soc-search-client");
            thread.setDaemon(true);
            return thread;
        });
        return new LlmHttpTransport(client, worker, new LlmHttpTransport.DeadlineScheduler() {
            @Override
            public LlmHttpTransport.Cancellable schedule(Runnable callback, Duration delay) {
                var scheduled = timer.schedule(callback, delay.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
                return () -> scheduled.cancel(false);
            }

            @Override
            public void close() {
                timer.shutdownNow();
            }
        }, false);
    }

    /** Requests a validated search proposal without blocking the caller. */
    public CompletableFuture<Result> propose(String buyerText) {
        if (closed.get()) {
            return completed(Failure.CLOSED);
        }
        if (buyerText == null || buyerText.strip().isEmpty()
                || buyerText.strip().codePointCount(0, buyerText.strip().length()) > 1_000) {
            return completed(Failure.INVALID_INPUT);
        }
        String cleanText = buyerText.strip();
        String inputData = GSON.toJson(Map.of("buyerText", cleanText));
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", INSTRUCTIONS),
                Map.of("role", "user", "content", inputData));
        String requestBody = GSON.toJson(Map.of("model", model, "messages", messages,
                "reasoning_effort", "none"));
        byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        if (requestBytes.length > MAX_REQUEST_BYTES) {
            return completed(Failure.INVALID_INPUT);
        }

        Call call = new Call();
        if (closed.get()) {
            return completed(Failure.CLOSED);
        }
        calls.add(call);
        if (closed.get()) {
            call.finish(Failure.CLOSED);
            return call.result;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(deadline)
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "identity")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes)).build();
            call.exchange = transport.send(request, deadline, () -> { }, info -> bodyHandler(call, info));
            call.exchange.result().whenComplete((response, failure) -> {
                if (failure != null) {
                    call.finish(mapFailure(failure));
                    return;
                }
                try {
                    transport.execute(() -> process(call, response));
                } catch (RuntimeException rejected) {
                    call.finish(Failure.CLOSED);
                }
            });
        } catch (RuntimeException exception) {
            call.finish(Failure.NETWORK);
        }
        return call.result;
    }

    private HttpResponse.BodySubscriber<byte[]> bodyHandler(Call call, HttpResponse.ResponseInfo response) {
        Failure failure = responseFailure(response);
        if (failure != null) {
            call.responseFailure = failure;
            return LlmHttpTransport.cancelledBody();
        }
        return new BoundedLlmBodySubscriber(false, transport.worker());
    }

    private void process(Call call, HttpResponse<byte[]> response) {
        if (call.responseFailure != null) {
            call.finish(call.responseFailure);
            return;
        }
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            call.finish(Failure.PROVIDER_AUTH);
            return;
        }
        if (status == 429 || status >= 500) {
            call.finish(Failure.PROVIDER_BUSY);
            return;
        }
        if (status != 200 || !identityEncoding(response)) {
            call.finish(Failure.INVALID_RESPONSE);
            return;
        }
        try {
            String json = decodeUtf8(response.body());
            Object envelopeValue = parseStrict(json);
            String content = chatContent(envelopeValue);
            Object proposal = parseStrict(content);
            if (!(proposal instanceof Map<?, ?> proposalMap)) {
                call.finish(Failure.INVALID_RESPONSE);
                return;
            }
            ListingSearch search = AiSearchProposalValidator.validate(proposalMap);
            call.finish(new Proposed(search));
        } catch (IOException | RuntimeException exception) {
            call.finish(Failure.INVALID_RESPONSE);
        }
    }

    private static boolean identityEncoding(HttpResponse<?> response) {
        return identityEncoding(response.headers().allValues("Content-Encoding"));
    }

    private static boolean identityEncoding(HttpResponse.ResponseInfo response) {
        return identityEncoding(response.headers().allValues("Content-Encoding"));
    }

    private static boolean identityEncoding(List<String> values) {
        return values.isEmpty() || values.size() == 1 && "identity".equalsIgnoreCase(values.get(0).trim());
    }

    private static Failure responseFailure(HttpResponse.ResponseInfo response) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            return Failure.PROVIDER_AUTH;
        }
        if (status == 429 || status >= 500) {
            return Failure.PROVIDER_BUSY;
        }
        if (status != 200 || !identityEncoding(response)) {
            return Failure.INVALID_RESPONSE;
        }
        try {
            if (response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_RESPONSE_BYTES) {
                return Failure.INVALID_RESPONSE;
            }
        } catch (NumberFormatException failure) {
            return Failure.INVALID_RESPONSE;
        }
        return null;
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Object parseStrict(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new java.io.StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            Object result = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException();
            }
            return result;
        }
    }

    private static Object readValue(JsonReader reader, int parentDepth) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.BEGIN_OBJECT) {
            int depth = parentDepth + 1;
            requireDepth(depth);
            Map<String, Object> object = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (object.containsKey(name)) {
                    throw new IOException();
                }
                object.put(name, readValue(reader, depth));
            }
            reader.endObject();
            return object;
        }
        if (token == JsonToken.BEGIN_ARRAY) {
            int depth = parentDepth + 1;
            requireDepth(depth);
            List<Object> array = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
                array.add(readValue(reader, depth));
            }
            reader.endArray();
            return array;
        }
        return switch (token) {
            case STRING -> reader.nextString();
            case NUMBER -> parseInteger(reader.nextString());
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> {
                reader.nextNull();
                yield null;
            }
            default -> throw new IOException();
        };
    }

    private static void requireDepth(int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException();
        }
    }

    private static BigInteger parseInteger(String number) throws IOException {
        if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
            throw new IOException();
        }
        try {
            return new BigInteger(number);
        } catch (NumberFormatException exception) {
            throw new IOException();
        }
    }

    private static String chatContent(Object value) throws IOException {
        if (!(value instanceof Map<?, ?> response) || !(response.get("choices") instanceof List<?> choices)
                || choices.isEmpty()) {
            throw new IOException();
        }
        String firstContent = null;
        for (int index = 0; index < choices.size(); index++) {
            Object choiceValue = choices.get(index);
            if (!(choiceValue instanceof Map<?, ?> choice) || !"stop".equals(choice.get("finish_reason"))
                    || !(choice.get("message") instanceof Map<?, ?> message)
                    || !"assistant".equals(message.get("role"))
                    || !(message.get("content") instanceof String content) || content.isBlank()) {
                throw new IOException();
            }
            if (message.containsKey("refusal") && message.get("refusal") != null
                    || message.containsKey("function_call") && message.get("function_call") != null
                    || message.containsKey("tool_calls") && message.get("tool_calls") != null
                            && (!(message.get("tool_calls") instanceof List<?> tools) || !tools.isEmpty())) {
                throw new IOException();
            }
            if (index == 0) {
                firstContent = content;
            }
        }
        return firstContent;
    }

    private static boolean validEndpoint(URI endpoint) {
        if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null) {
            return false;
        }
        String path = endpoint.getPath();
        if (path == null) {
            return true;
        }
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }
        return !path.endsWith("/responses") && !path.endsWith("/chat/completions");
    }

    private static URI chatCompletionsEndpoint(URI base) {
        String normalized = base.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized + "/chat/completions");
    }

    private static boolean validToken(String token) {
        return token != null && !token.isBlank() && token.chars().noneMatch(Character::isISOControl);
    }

    private static CompletableFuture<Result> completed(Failure failure) {
        return CompletableFuture.completedFuture(new Failed(failure));
    }

    private static Failure mapFailure(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        if (failure instanceof java.net.http.HttpTimeoutException) {
            return Failure.TIMEOUT;
        }
        if (failure instanceof BoundedLlmBodySubscriber.InvalidBodyException) {
            return Failure.INVALID_RESPONSE;
        }
        if (failure instanceof LlmHttpTransport.TransportClosedException) {
            return Failure.CLOSED;
        }
        if (failure instanceof LlmHttpTransport.ApplicationBudgetException) {
            return Failure.PROVIDER_BUSY;
        }
        return Failure.NETWORK;
    }

    /** Closes this client and cancels outstanding requests. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            calls.forEach(call -> call.finish(Failure.CLOSED));
            if (ownsTransport) {
                transport.close();
            }
        }
    }

    private final class Call {
        private final CompletableFuture<Result> result = new CompletableFuture<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile LlmHttpTransport.Exchange exchange;
        private volatile Failure responseFailure;

        private void finish(Failure failure) {
            finish(new Failed(failure));
        }

        private void finish(Result value) {
            if (finished.compareAndSet(false, true)) {
                calls.remove(this);
                LlmHttpTransport.Exchange pending = exchange;
                if (pending != null) {
                    pending.cancel();
                }
                result.complete(value);
            }
        }
    }
}
