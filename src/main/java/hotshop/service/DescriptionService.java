package hotshop.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import hotshop.llm.LlmClient;
import hotshop.model.Condition;

/** Session-bound unsaved listing-description generation. */
public final class DescriptionService implements AutoCloseable {
    private static final int MAX_TITLE_CODE_POINTS = 120;
    private static final System.Logger LOGGER = System.getLogger(DescriptionService.class.getName());

    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final DescriptionQuota quota;
    private final LlmClient client;
    private final Set<Pending> requests = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    /** Uses caller-owned session/worker/transport and a shared server-owned quota registry. */
    public DescriptionService(ServiceWorker worker, AuthenticatedSession session, DescriptionQuota quota,
            LlmClient client) {
        this.worker = Objects.requireNonNull(worker);
        this.session = Objects.requireNonNull(session);
        this.quota = Objects.requireNonNull(quota);
        this.client = Objects.requireNonNull(client);
    }

    /** Generates an unsaved description; cancelling the returned future does not free an active provider slot. */
    public CompletableFuture<GenerationResult> generateDescription(String title, Condition condition) {
        Pending request;
        synchronized (this) {
            if (closed) {
                return CompletableFuture.completedFuture(failed(FailureKind.CLOSED));
            }
            request = new Pending();
            requests.add(request);
        }
        submit(request, () -> request.begin(title, condition));
        return request.result;
    }

    /** Stops this service's requests without closing injected resources or clearing shared quota history. */
    @Override
    public void close() {
        List<Pending> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = List.copyOf(requests);
        }
        pending.forEach(Pending::close);
    }

    private void submit(Pending request, Runnable action) {
        worker.submit(() -> {
            action.run();
            return null;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                request.finish(closed ? failed(FailureKind.CLOSED) : unexpected());
            }
        });
    }

    private final class Pending {
        private final CompletableFuture<GenerationResult> result = new CompletableFuture<>();
        private UUID account;
        private long revision;
        private CompletableFuture<LlmClient.PreparedRequest> preparation;
        private DescriptionQuota.Reservation reservation;
        private LlmClient.Attempt attempt;
        private CompletableFuture<LlmClient.Result> transport;
        private boolean transportFinished;
        private boolean finished;

        synchronized void begin(String title, Condition condition) {
            if (finished || closed) {
                finish(failed(FailureKind.CLOSED));
                return;
            }
            account = session.getCurrentUserId().orElse(null);
            if (account == null) {
                finish(failed(FailureKind.LOGIN_REQUIRED));
                return;
            }
            revision = session.revision();
            if (title == null || condition == null || title.strip().isEmpty()
                    || title.strip().codePointCount(0, title.strip().length()) > MAX_TITLE_CODE_POINTS) {
                finish(failed(FailureKind.INVALID_INPUT));
                return;
            }
            try {
                preparation = Objects.requireNonNull(client.prepare(title.strip(), condition));
                preparation.whenComplete((prepared, failure) -> submit(this, () -> admit(prepared, failure)));
            } catch (RuntimeException failure) {
                finishOnWorker(preparationFailure(failure));
            }
        }

        synchronized void admit(LlmClient.PreparedRequest prepared, Throwable failure) {
            if (finished) {
                return;
            }
            GenerationResult guard = guardFailure();
            if (guard != null) {
                finish(guard);
                return;
            }
            if (failure != null || prepared == null) {
                finishOnWorker(failure == null ? unexpected() : preparationFailure(failure));
                return;
            }
            DescriptionQuota.Admission admission = quota.reserve(account);
            if (!admission.admitted()) {
                finish(admission.rejection() == DescriptionQuota.Rejection.ACTIVE_REQUEST
                        ? failed(FailureKind.IN_FLIGHT)
                        : new Failed(FailureKind.RATE_LIMITED, admission.retryAfterSeconds()));
                return;
            }
            reservation = admission.reservation();
            try {
                DescriptionQuota dispatchQuota = quota;
                DescriptionQuota.Reservation dispatchReservation = reservation;
                attempt = Objects.requireNonNull(prepared.dispatch(() -> {
                    if (!dispatchQuota.markDispatched(dispatchReservation)) {
                        throw new IllegalStateException("Description dispatch reservation is unavailable");
                    }
                }));
                transport = Objects.requireNonNull(attempt.result());
                transport.whenComplete(this::transportComplete);
            } catch (RuntimeException dispatchFailure) {
                // A broken collaborator returned no observable terminal attempt: retain its charge conservatively.
                transportFinished = true;
                quota.release(reservation);
                finishOnWorker(unexpected());
            }
        }

        void transportComplete(LlmClient.Result outcome, Throwable failure) {
            synchronized (this) {
                if (transportFinished) {
                    return;
                }
                transportFinished = true;
                boolean dispatched = true;
                try {
                    dispatched = attempt.wasDispatched();
                } catch (RuntimeException malformedAttempt) {
                    // Unknown dispatch ownership must not refund an attempted call.
                    LOGGER.log(System.Logger.Level.WARNING, "LLM_SERVICE_UNEXPECTED");
                }
                if (dispatched) {
                    quota.release(reservation);
                } else {
                    quota.rollback(reservation);
                }
            }
            GenerationResult mapped = failure == null ? mapResult(outcome) : unexpected();
            submit(this, () -> finishOnWorker(mapped));
        }

        private GenerationResult guardFailure() {
            if (closed) {
                return failed(FailureKind.CLOSED);
            }
            if (revision != session.revision() || !account.equals(session.getCurrentUserId().orElse(null))) {
                return failed(FailureKind.SESSION_CHANGED);
            }
            return null;
        }

        void finishOnWorker(GenerationResult outcome) {
            GenerationResult guard = guardFailure();
            finish(guard == null ? outcome : guard);
        }

        void finish(GenerationResult outcome) {
            synchronized (this) {
                if (finished) {
                    return;
                }
                finished = true;
            }
            requests.remove(this);
            result.complete(outcome);
        }

        void close() {
            CompletableFuture<?> current;
            synchronized (this) {
                current = transport == null ? preparation : transport;
            }
            if (current != null) {
                current.cancel(true);
            }
            finish(failed(FailureKind.CLOSED));
        }
    }

    private static GenerationResult preparationFailure(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        if (failure instanceof IllegalArgumentException) {
            return failed(FailureKind.INVALID_INPUT);
        }
        if (failure instanceof LlmClient.PreparationException preparationFailure) {
            return failed(FailureKind.valueOf(preparationFailure.kind().name()));
        }
        return unexpected();
    }

    private static GenerationResult mapResult(LlmClient.Result outcome) {
        if (outcome instanceof LlmClient.Generated generated && generated.description() != null) {
            return new Generated(generated.description());
        }
        if (outcome instanceof LlmClient.Failed failure && failure.kind() != null) {
            return failed(FailureKind.valueOf(failure.kind().name()));
        }
        return unexpected();
    }

    private static Failed failed(FailureKind kind) {
        return new Failed(kind, 0);
    }

    private static Failed unexpected() {
        LOGGER.log(System.Logger.Level.WARNING, "LLM_SERVICE_UNEXPECTED");
        return failed(FailureKind.UNAVAILABLE);
    }

    public sealed interface GenerationResult permits Generated, Failed {
    }

    public record Generated(String description) implements GenerationResult {
    }

    public record Failed(FailureKind kind, long retryAfterSeconds) implements GenerationResult {
    }

    public enum FailureKind {
        INVALID_INPUT, LOGIN_REQUIRED, SESSION_CHANGED, UNAVAILABLE, IN_FLIGHT, RATE_LIMITED,
        TIMEOUT, PROVIDER_AUTH, PROVIDER_BUSY, NETWORK, INVALID_RESPONSE, CLOSED
    }
}
