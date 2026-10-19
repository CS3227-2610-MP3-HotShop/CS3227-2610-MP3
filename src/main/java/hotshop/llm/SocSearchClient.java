package hotshop.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import hotshop.service.ListingSearch;

/** Common SoC search client contract; transport and response handling are not implemented here. */
public final class SocSearchClient implements AutoCloseable {
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

    /** Creates a client using the standard HTTP transport. */
    public SocSearchClient(URI endpoint, String bearerToken, String model) {
        this(endpoint, bearerToken, model, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build(), Duration.ofSeconds(45));
    }

    SocSearchClient(URI endpoint, String bearerToken, String model, HttpClient client, Duration deadline) {
        // Parameters define the shared API. Validation and behavior are intentionally deferred.
    }

    /** Requests a validated search proposal without blocking the caller. */
    public CompletableFuture<Result> propose(String buyerText) {
        throw new UnsupportedOperationException("SoC search transport is not implemented");
    }

    /** Closes this client. */
    @Override
    public void close() {
        // The declaration stub owns no resources.
    }
}
