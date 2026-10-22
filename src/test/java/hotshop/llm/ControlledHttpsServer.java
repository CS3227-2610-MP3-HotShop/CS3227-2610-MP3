package hotshop.llm;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/** Loopback-only HTTPS with an isolated synthetic CA, never global TLS overrides. */
final class ControlledHttpsServer implements AutoCloseable {
    private static final char[] STORE_PASSWORD = "public-test-fixture".toCharArray();
    private final HttpsServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile Response response = new Response(200, Map.of("Content-Type", "application/json"), new byte[0],
            false, null, false);
    private volatile Function<Request, Response> responder;

    ControlledHttpsServer() throws GeneralSecurityException, IOException {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream stream = resource("server.p12")) {
            keys.load(stream, STORE_PASSWORD);
        }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, STORE_PASSWORD);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(managers.getKeyManagers(), null, null);
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                // Synthetic fixture inputs only. Bound capture independently of future client limits.
                byte[] body = exchange.getRequestBody().readNBytes(8193);
                Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI(),
                        Map.copyOf(exchange.getRequestHeaders()), body);
                requestCount.incrementAndGet();
                requests.add(request);
                Function<Request, Response> currentResponder = responder;
                Response current = currentResponder == null ? response : currentResponder.apply(request);
                current.headers().forEach(exchange.getResponseHeaders()::set);
                exchange.sendResponseHeaders(current.status(), current.chunked() ? 0 : current.body().length);
                if (current.stallGate() == null) {
                    if (current.disconnect()) {
                        exchange.getResponseBody().write(current.body(), 0, current.body().length / 2);
                        exchange.getResponseBody().close();
                        return;
                    }
                    exchange.getResponseBody().write(current.body());
                } else {
                    int split = current.body().length / 2;
                    exchange.getResponseBody().write(current.body(), 0, split);
                    exchange.getResponseBody().flush();
                    current.stallGate().started().countDown();
                    try {
                        current.stallGate().release().await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException("HTTPS fixture response interrupted", exception);
                    }
                    exchange.getResponseBody().write(current.body(), split, current.body().length - split);
                }
            }
        });
        server.start();
    }

    URI uri(String path) {
        return URI.create("https://localhost:" + server.getAddress().getPort() + path);
    }

    void respond(int status, String contentType, String body) {
        respondBytes(status, Map.of("Content-Type", contentType), body.getBytes(StandardCharsets.UTF_8), false);
    }

    void respondBytes(int status, Map<String, String> headers, byte[] body, boolean chunked) {
        response = new Response(status, Map.copyOf(headers), body.clone(), chunked, null, false);
    }

    void respondDisconnected(int status, Map<String, String> headers, byte[] body) {
        response = new Response(status, Map.copyOf(headers), body.clone(), false, null, true);
    }

    StallGate respondStalled(int status, Map<String, String> headers, byte[] body) {
        StallGate gate = new StallGate(new CountDownLatch(1), new CountDownLatch(1));
        response = new Response(status, Map.copyOf(headers), body.clone(), true, gate, false);
        return gate;
    }

    void respondForRequests(Function<Request, Response> requestResponder) {
        responder = requestResponder;
    }

    void redirect(URI destination) {
        respondBytes(302, Map.of("Content-Type", "application/json", "Location", destination.toString()),
                new byte[0], false);
    }

    void respondChunked(String body) {
        respondBytes(200, Map.of("Content-Type", "application/json"), body.getBytes(StandardCharsets.UTF_8), true);
    }

    Request takeRequest() throws InterruptedException {
        Request request = requests.poll(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("Expected a captured loopback HTTPS request");
        }
        return request;
    }

    boolean hasRequest(long timeout, TimeUnit unit) throws InterruptedException {
        Request request = requests.poll(timeout, unit);
        if (request == null) {
            return false;
        }
        requests.add(request);
        return true;
    }

    int requestCount() {
        return requestCount.get();
    }

    HttpClient trustedClient() throws GeneralSecurityException, IOException {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        try (InputStream stream = resource("test-ca.crt")) {
            trust.setCertificateEntry("test-ca", CertificateFactory.getInstance("X.509").generateCertificate(stream));
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, managers.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static InputStream resource(String name) throws IOException {
        InputStream stream = ControlledHttpsServer.class.getResourceAsStream("/hotshop/llm/" + name);
        if (stream == null) {
            throw new IOException("Missing synthetic HTTPS fixture");
        }
        return stream;
    }

    record Request(String method, URI uri, Map<String, List<String>> headers, byte[] body) {
        String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream()).findFirst().orElse(null);
        }

        String authorization() {
            return header("Authorization");
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    record StallGate(CountDownLatch started, CountDownLatch release) {
    }

    record Response(int status, Map<String, String> headers, byte[] body, boolean chunked,
            StallGate stallGate, boolean disconnect) {
        Response(int status, String contentType, byte[] body, Map<String, String> headers, boolean chunked) {
            this(status, withContentType(headers, contentType), body, chunked, null, false);
        }

        private static Map<String, String> withContentType(Map<String, String> headers, String contentType) {
            java.util.HashMap<String, String> result = new java.util.HashMap<>(headers);
            result.put("Content-Type", contentType);
            return Map.copyOf(result);
        }
    }
}
