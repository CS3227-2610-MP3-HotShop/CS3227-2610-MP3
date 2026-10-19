package hotshop.llm;

import java.util.concurrent.CompletableFuture;

import hotshop.model.Condition;

/** Injectable transport boundary: product facts in, validated text or fixed safe failure out. */
public interface LlmClient {
    CompletableFuture<Result> generate(String title, Condition condition);

    sealed interface Result permits Generated, Failed {
    }

    record Generated(String description) implements Result {
    }

    record Failed(Failure kind) implements Result {
    }

    enum Failure {
        UNAVAILABLE, TIMEOUT, PROVIDER_AUTH, PROVIDER_BUSY, NETWORK, INVALID_RESPONSE, CLOSED
    }
}
