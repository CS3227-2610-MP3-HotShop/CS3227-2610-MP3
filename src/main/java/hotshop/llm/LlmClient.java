package hotshop.llm;

import java.util.concurrent.CompletableFuture;

import hotshop.model.Condition;

/** Injectable transport boundary: product facts in, validated text or fixed safe failure out. */
public interface LlmClient {
    CompletableFuture<Result> generate(String title, Condition condition);

    /** Encodes and checks private request bytes asynchronously, before account quota admission. */
    CompletableFuture<PreparedRequest> prepare(String title, Condition condition);

    /** Opaque, single-use request capability; contains no caller-readable private configuration. */
    interface PreparedRequest {
        default Attempt dispatch() {
            return dispatch(() -> { });
        }

        /**
         * Starts a nonblocking attempt, returning its observable terminal lifecycle even for local rejection.
         * Implementations must never throw after attempting HTTP; missing/malformed attempts cannot prove a refund.
         *
         * @param beforeSend trusted, nonblocking quota hook run once on the transport executor immediately before
         *     the HTTP attempt, inside its cancellation gate; it must not acquire session/service locks
         * @return one attempt whose dispatch marker is stable before its result becomes terminal
         */
        Attempt dispatch(Runnable beforeSend);
    }

    /** Local dispatch lifecycle; inspect dispatch ownership only after the result is terminal. */
    interface Attempt {
        CompletableFuture<Result> result();

        boolean wasDispatched();
    }

    /** Fixed preparation failure without private request, configuration or nested cause. */
    final class PreparationException extends RuntimeException {
        private final Failure kind;

        public PreparationException(Failure kind) {
            super("Description generation preparation failed");
            this.kind = java.util.Objects.requireNonNull(kind);
        }

        public Failure kind() {
            return kind;
        }
    }

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
