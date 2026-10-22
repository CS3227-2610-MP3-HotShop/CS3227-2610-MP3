package hotshop.llm;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owns the bounded asynchronous HTTPS resources shared by the application AI clients. */
public final class LlmHttpTransport implements AutoCloseable {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private final Set<Exchange> exchanges = new HashSet<>();
    private final boolean ownsHttpClient;
    private HttpClient client;
    private ExecutorService worker;
    private DeadlineScheduler scheduler;
    private boolean closed;

    /** Creates an idle transport; its threads and HTTP client are initialized on first use. */
    public LlmHttpTransport() {
        ownsHttpClient = true;
    }

    LlmHttpTransport(HttpClient client, ExecutorService worker, DeadlineScheduler scheduler) {
        this(client, worker, scheduler, true);
    }

    LlmHttpTransport(HttpClient client, ExecutorService worker, DeadlineScheduler scheduler,
            boolean ownsHttpClient) {
        this.client = Objects.requireNonNull(client);
        this.worker = Objects.requireNonNull(worker);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.ownsHttpClient = ownsHttpClient;
    }

    static HttpClient createHttpClient(Executor executor) {
        return HttpClient.newBuilder().executor(executor).connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    Exchange send(HttpRequest request, Duration timeout, Runnable beforeSend,
            HttpResponse.BodyHandler<byte[]> bodyHandler) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(beforeSend);
        Objects.requireNonNull(bodyHandler);
        Exchange exchange;
        synchronized (this) {
            if (closed) {
                return Exchange.rejected(this, new TransportClosedException());
            }
            try {
                initializeResources();
                exchange = new Exchange(this, request, beforeSend, bodyHandler);
                exchanges.add(exchange);
            } catch (RuntimeException failure) {
                return Exchange.rejected(this, failure);
            }
        }
        try {
            exchange.registerDeadline(scheduler.schedule(
                    () -> exchange.finishExceptionally(new HttpTimeoutException("AI request timed out")), timeout));
        } catch (RuntimeException failure) {
            exchange.finishExceptionally(failure);
            return exchange;
        }
        synchronized (this) {
            if (closed) {
                exchange.finishExceptionally(new TransportClosedException());
                return exchange;
            }
            try {
                worker.execute(exchange::dispatch);
            } catch (RuntimeException failure) {
                exchange.finishExceptionally(new AdmissionException());
            }
        }
        return exchange;
    }

    private void initializeResources() {
        if (worker == null) {
            worker = Executors.newVirtualThreadPerTaskExecutor();
            scheduler = new ScheduledDeadline();
            client = createHttpClient(worker);
        }
    }

    private synchronized void remove(Exchange exchange) {
        exchanges.remove(exchange);
    }

    void execute(Runnable task) {
        ExecutorService current;
        synchronized (this) {
            if (closed) {
                throw new java.util.concurrent.RejectedExecutionException("AI transport closed");
            }
            initializeResources();
            current = worker;
        }
        current.execute(task);
    }

    Executor worker() {
        synchronized (this) {
            if (closed) {
                throw new java.util.concurrent.RejectedExecutionException("AI transport closed");
            }
            initializeResources();
            return worker;
        }
    }

    @Override
    public void close() {
        List<Exchange> pending;
        HttpClient currentClient;
        ExecutorService currentWorker;
        DeadlineScheduler currentScheduler;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = List.copyOf(exchanges);
            currentClient = client;
            currentWorker = worker;
            currentScheduler = scheduler;
        }
        pending.forEach(Exchange::markOwnerClosed);
        pending.forEach(Exchange::completeOwnerClosed);
        if (ownsHttpClient && currentClient != null) {
            currentClient.shutdownNow();
        }
        if (currentScheduler != null) {
            currentScheduler.close();
        }
        if (currentWorker != null) {
            currentWorker.shutdownNow();
        }
    }

    interface DeadlineScheduler extends AutoCloseable {
        Cancellable schedule(Runnable callback, Duration delay);

        @Override
        default void close() {
        }
    }

    interface Cancellable {
        void cancel();
    }

    static final class ScheduledDeadline implements DeadlineScheduler {
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "hotshop-ai-deadline");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public Cancellable schedule(Runnable callback, Duration delay) {
            var future = timer.schedule(callback, delay.toNanos(), TimeUnit.NANOSECONDS);
            return () -> future.cancel(false);
        }

        @Override
        public void close() {
            timer.shutdownNow();
        }
    }

    static final class Exchange {
        private final LlmHttpTransport owner;
        private final HttpRequest request;
        private final Runnable beforeSend;
        private final HttpResponse.BodyHandler<byte[]> bodyHandler;
        private final CompletableFuture<HttpResponse<byte[]>> result = new CompletableFuture<>();
        private CompletableFuture<HttpResponse<byte[]>> pending;
        private BoundedLlmBodySubscriber subscriber;
        private Cancellable deadline;
        private boolean terminal;
        private boolean sending;
        private boolean dispatched;
        private Throwable deferredFailure;
        private boolean ownerClosePending;

        private Exchange(LlmHttpTransport owner, HttpRequest request, Runnable beforeSend,
                HttpResponse.BodyHandler<byte[]> bodyHandler) {
            this.owner = owner;
            this.request = request;
            this.beforeSend = beforeSend;
            this.bodyHandler = bodyHandler;
        }

        static Exchange rejected(LlmHttpTransport owner, Throwable failure) {
            Exchange exchange = new Exchange(owner, null, () -> { }, ignored -> cancelledBody());
            exchange.result.completeExceptionally(failure);
            exchange.terminal = true;
            return exchange;
        }

        CompletableFuture<HttpResponse<byte[]>> result() {
            return result;
        }

        synchronized boolean wasDispatched() {
            return dispatched;
        }

        private synchronized void registerDeadline(Cancellable incoming) {
            deadline = incoming;
            if (terminal) {
                incoming.cancel();
            }
        }

        private void dispatch() {
            synchronized (this) {
                if (terminal || owner.isClosed()) {
                    return;
                }
            }
            try {
                beforeSend.run();
            } catch (RuntimeException failure) {
                finishExceptionally(new AdmissionException());
                return;
            }
            CompletableFuture<HttpResponse<byte[]>> sent;
            synchronized (this) {
                if (terminal || owner.isClosed()) {
                    return;
                }
                sending = true;
                dispatched = true;
            }
            try {
                sent = owner.client().sendAsync(request, this::handleBody);
            } catch (RuntimeException failure) {
                Throwable deferred;
                synchronized (this) {
                    sending = false;
                    deferred = deferredFailure;
                }
                if (deferred != null) {
                    completeExceptionally(deferred);
                    return;
                }
                finishExceptionally(failure);
                return;
            }
            Throwable deferred;
            synchronized (this) {
                pending = sent;
                sending = false;
                deferred = deferredFailure;
            }
            if (deferred != null) {
                sent.cancel(true);
                completeExceptionally(deferred);
                return;
            }
            sent.whenComplete((response, failure) -> {
                if (failure != null) {
                    finishExceptionally(unwrap(failure));
                } else {
                    finish(response);
                }
            });
        }

        private HttpResponse.BodySubscriber<byte[]> handleBody(HttpResponse.ResponseInfo info) {
            HttpResponse.BodySubscriber<byte[]> incoming;
            try {
                incoming = bodyHandler.apply(info);
            } catch (RuntimeException failure) {
                finishExceptionally(failure);
                return cancelledBody();
            }
            if (incoming instanceof BoundedLlmBodySubscriber bounded) {
                synchronized (this) {
                    subscriber = bounded;
                    if (terminal) {
                        bounded.cancel();
                    }
                }
            }
            return incoming;
        }

        private void finish(HttpResponse<byte[]> response) {
            Throwable closedFailure = null;
            synchronized (this) {
                if (terminal) {
                    return;
                }
                terminal = true;
                if (owner.isClosed()) {
                    closedFailure = new TransportClosedException();
                }
            }
            if (closedFailure == null) {
                complete(response);
            } else {
                completeExceptionally(closedFailure);
            }
        }

        void finishExceptionally(Throwable failure) {
            synchronized (this) {
                if (terminal) {
                    return;
                }
                if (owner.isClosed()) {
                    failure = new TransportClosedException();
                }
                terminal = true;
                if (sending && pending == null) {
                    deferredFailure = failure;
                    return;
                }
            }
            completeExceptionally(failure);
        }

        private synchronized void markOwnerClosed() {
            if (terminal) {
                return;
            }
            terminal = true;
            deferredFailure = new TransportClosedException();
            ownerClosePending = true;
        }

        private void completeOwnerClosed() {
            synchronized (this) {
                if (!ownerClosePending || sending && pending == null) {
                    return;
                }
                ownerClosePending = false;
            }
            completeExceptionally(new TransportClosedException());
        }

        void cancel() {
            finishExceptionally(new java.util.concurrent.CancellationException());
        }

        private void complete(HttpResponse<byte[]> response) {
            cleanup();
            result.complete(response);
        }

        private void completeExceptionally(Throwable failure) {
            cleanup();
            result.completeExceptionally(failure);
        }

        private void cleanup() {
            CompletableFuture<HttpResponse<byte[]>> currentPending;
            BoundedLlmBodySubscriber currentSubscriber;
            Cancellable currentDeadline;
            synchronized (this) {
                currentPending = pending;
                currentSubscriber = subscriber;
                currentDeadline = deadline;
            }
            if (currentDeadline != null) {
                currentDeadline.cancel();
            }
            if (currentSubscriber != null) {
                currentSubscriber.cancel();
            }
            if (currentPending != null && !currentPending.isDone()) {
                currentPending.cancel(true);
            }
            owner.remove(this);
        }
    }

    private synchronized boolean isClosed() {
        return closed;
    }

    private synchronized HttpClient client() {
        return client;
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    static HttpResponse.BodySubscriber<byte[]> cancelledBody() {
        return new HttpResponse.BodySubscriber<>() {
            private final CompletableFuture<byte[]> body = CompletableFuture.completedFuture(new byte[0]);

            @Override
            public java.util.concurrent.CompletionStage<byte[]> getBody() {
                return body;
            }

            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.cancel();
            }

            @Override
            public void onNext(List<java.nio.ByteBuffer> item) {
            }

            @Override
            public void onError(Throwable failure) {
            }

            @Override
            public void onComplete() {
            }
        };
    }

    static final class TransportClosedException extends java.io.IOException {
        private static final long serialVersionUID = 1L;

        TransportClosedException() {
            super("AI transport closed");
        }
    }

    static final class AdmissionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AdmissionException() {
            super("AI request admission failed");
        }
    }
}
