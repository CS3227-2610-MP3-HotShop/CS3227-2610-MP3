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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
    private volatile Response response = new Response(200, "application/json", new byte[0]);

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
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        new String(body, StandardCharsets.UTF_8)));
                Response current = response;
                exchange.getResponseHeaders().set("Content-Type", current.contentType());
                exchange.sendResponseHeaders(current.status(), current.body().length);
                exchange.getResponseBody().write(current.body());
            }
        });
        server.start();
    }

    URI uri(String path) {
        return URI.create("https://localhost:" + server.getAddress().getPort() + path);
    }

    void respond(int status, String contentType, String body) {
        response = new Response(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    Request takeRequest() throws InterruptedException {
        Request request = requests.poll(5, TimeUnit.SECONDS);
        if (request == null) {
            throw new AssertionError("Expected a captured loopback HTTPS request");
        }
        return request;
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

    record Request(String method, URI uri, String authorization, String body) {
    }

    private record Response(int status, String contentType, byte[] body) {
    }
}
