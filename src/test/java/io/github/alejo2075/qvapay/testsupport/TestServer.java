package io.github.alejo2075.qvapay.testsupport;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** Loopback HTTP server for tests: records requests and answers with scripted responses. */
public final class TestServer implements AutoCloseable {

    /** A request as received by the server. */
    public record Recorded(
            String method,
            String rawPath,
            String query,
            Map<String, List<String>> headers,
            byte[] body) {

        public String header(String name) {
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue().get(0);
                }
            }
            return null;
        }

        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** A scripted response. */
    public record Stub(int status, Map<String, String> headers, String body, long delayMillis) {

        public static Stub json(int status, String body) {
            return new Stub(status, Map.of("Content-Type", "application/json"), body, 0);
        }

        public static Stub of(int status, Map<String, String> headers, String body) {
            return new Stub(status, headers, body, 0);
        }

        public Stub delayed(long millis) {
            return new Stub(status, headers, body, millis);
        }
    }

    private final HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    private TestServer(HttpServer server) {
        this.server = server;
    }

    /** Starts a server that answers every request with the handler's stub. */
    public static TestServer start(Function<Recorded, Stub> handler) {
        try {
            HttpServer server =
                    HttpServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            TestServer ts = new TestServer(server);
            server.createContext(
                    "/",
                    exchange -> {
                        byte[] body = exchange.getRequestBody().readAllBytes();
                        Recorded rec =
                                new Recorded(
                                        exchange.getRequestMethod(),
                                        exchange.getRequestURI().getRawPath(),
                                        exchange.getRequestURI().getRawQuery(),
                                        new java.util.HashMap<>(exchange.getRequestHeaders()),
                                        body);
                        ts.requests.add(rec);
                        Stub stub = handler.apply(rec);
                        if (stub.delayMillis() > 0) {
                            try {
                                Thread.sleep(stub.delayMillis());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        stub.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
                        byte[] out = stub.body().getBytes(StandardCharsets.UTF_8);
                        try {
                            exchange.sendResponseHeaders(
                                    stub.status(), out.length == 0 ? -1 : out.length);
                            if (out.length > 0) {
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(out);
                                }
                            }
                        } catch (IOException ignored) {
                            // client went away (timeout tests)
                        } finally {
                            exchange.close();
                        }
                    });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return ts;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Starts a server that always answers with the same stub. */
    public static TestServer start(Stub stub) {
        return start(r -> stub);
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public List<Recorded> requests() {
        return Collections.unmodifiableList(new ArrayList<>(requests));
    }

    @Override
    public void close() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }
}
