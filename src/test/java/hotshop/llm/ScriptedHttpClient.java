package hotshop.llm;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/** Synthetic transport that still exercises the production body handler and subscriber. */
final class ScriptedHttpClient extends HttpClient {
    final CountDownLatch dispatched = new CountDownLatch(1);
    final AtomicInteger calls = new AtomicInteger();
    final AtomicBoolean subscriptionCancelled = new AtomicBoolean();
    final CountDownLatch futureCancelled = new CountDownLatch(1);
    final List<CompletableFuture<?>> pendingExchanges = new CopyOnWriteArrayList<>();
    volatile HttpRequest request;
    volatile Thread dispatchThread;
    volatile HttpResponse.BodySubscriber<?> subscriber;
    int status = 200;
    Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
    List<byte[]> chunks = List.of();
    Throwable failure;
    Throwable bodyFailure;
    boolean stalled;
    boolean stalledHeaders;
    volatile CompletableFuture<?> pendingExchange;
    Runnable beforeReturn = () -> { };
    int stallAfterChunks = Integer.MAX_VALUE;
    boolean deferSubscription;
    boolean combinedBuffers;
    volatile boolean cancelPeersWithIo;
    volatile boolean bodyDelivered;
    volatile Flow.Subscription subscription;

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest outgoing,
            HttpResponse.BodyHandler<T> handler) {
        request = outgoing;
        dispatchThread = Thread.currentThread();
        calls.incrementAndGet();
        if (stalledHeaders) {
            CompletableFuture<HttpResponse<T>> pending = new CompletableFuture<>();
            pendingExchange = pending;
            dispatched.countDown();
            return pending;
        }
        if (failure != null) {
            dispatched.countDown();
            return CompletableFuture.failedFuture(failure);
        }
        HttpResponse.BodySubscriber<T> receiver = handler.apply(new HttpResponse.ResponseInfo() {
            public int statusCode() {
                return status;
            }

            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            public Version version() {
                return Version.HTTP_1_1;
            }
        });
        subscriber = receiver;
        subscription = new Flow.Subscription() {
            private int index;
            private boolean finished;

            public synchronized void request(long count) {
                if (finished || subscriptionCancelled.get() || stalled) {
                    return;
                }
                while (count-- > 0 && index < chunks.size() && !subscriptionCancelled.get()
                        && index < stallAfterChunks) {
                    if (combinedBuffers) {
                        index = chunks.size();
                        receiver.onNext(chunks.stream().map(ByteBuffer::wrap).toList());
                    } else {
                        receiver.onNext(List.of(ByteBuffer.wrap(chunks.get(index++))));
                    }
                }
                if (index == chunks.size() && !finished && !subscriptionCancelled.get()) {
                    finished = true;
                    if (bodyFailure == null) {
                        bodyDelivered = true;
                        receiver.onComplete();
                    } else {
                        receiver.onError(bodyFailure);
                    }
                }
            }

            public void cancel() {
                subscriptionCancelled.set(true);
            }
        };
        if (!deferSubscription) {
            receiver.onSubscribe(subscription);
        }
        CompletableFuture<HttpResponse<T>> result = new CompletableFuture<>();
        pendingExchanges.add(result);
        receiver.getBody().whenComplete((body, error) -> {
            if (error == null) {
                result.complete(new Response<>(outgoing, status, HttpHeaders.of(headers, (a, b) -> true), body));
            } else {
                result.completeExceptionally(error);
            }
        });
        pendingExchange = result;
        result.whenComplete((response, error) -> {
            if (result.isCancelled()) {
                futureCancelled.countDown();
                if (cancelPeersWithIo) {
                    pendingExchanges.stream().filter(peer -> peer != result)
                            .forEach(peer -> peer.completeExceptionally(new IOException("peer connection aborted")));
                }
            }
        });
        dispatched.countDown();
        beforeReturn.run();
        return result;
    }

    void awaitDispatch() throws InterruptedException {
        if (!dispatched.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Synthetic HTTP dispatch did not occur");
        }
    }

    void lateBody(byte[] bytes) {
        subscriber.onNext(List.of(ByteBuffer.wrap(bytes)));
        subscriber.onComplete();
    }

    void subscribeLate() {
        subscriber.onSubscribe(subscription);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
        throw new AssertionError("Push callbacks must not be configured");
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        throw new AssertionError("Synchronous HTTP must not be used");
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.of(Duration.ofSeconds(10));
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
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
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

    private record Response<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body)
            implements HttpResponse<T> {
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        public java.net.URI uri() {
            return request.uri();
        }

        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
