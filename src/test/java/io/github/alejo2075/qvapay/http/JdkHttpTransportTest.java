package io.github.alejo2075.qvapay.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.exception.TransportException;
import io.github.alejo2075.qvapay.testsupport.TestServer;
import io.github.alejo2075.qvapay.testsupport.TestServer.Stub;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest {

    private final JdkHttpTransport transport =
            new JdkHttpTransport(Duration.ofSeconds(2), Duration.ofSeconds(5));

    private static ApiRequest post(URI base, String path, String body, Duration timeout) {
        return new ApiRequest(
                "POST",
                base.resolve(path),
                Map.of("Content-Type", "application/json", "app-id", "a1"),
                body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                timeout);
    }

    @Test
    void sendsMethodPathHeadersAndBodyAndReturnsResponse() {
        try (TestServer server = TestServer.start(Stub.json(200, "{\"ok\":true}"))) {
            ApiResponse r = transport.send(post(server.baseUri(), "/v2/info", "{\"a\":1}", null));
            assertEquals(200, r.statusCode());
            assertEquals("{\"ok\":true}", new String(r.body(), StandardCharsets.UTF_8));
            var rec = server.requests().get(0);
            assertEquals("POST", rec.method());
            assertEquals("/v2/info", rec.rawPath());
            assertEquals("a1", rec.header("app-id"));
            assertEquals("{\"a\":1}", rec.bodyText());
        }
    }

    @Test
    void errorStatusesAreReturnedNotThrown() {
        try (TestServer server = TestServer.start(Stub.json(500, "{\"error\":\"boom\"}"))) {
            ApiResponse r = transport.send(post(server.baseUri(), "/x", null, null));
            assertEquals(500, r.statusCode());
        }
    }

    @Test
    void redirectsAreNotFollowed() {
        try (TestServer server =
                TestServer.start(Stub.of(302, Map.of("Location", "http://evil.invalid/"), ""))) {
            ApiResponse r = transport.send(post(server.baseUri(), "/x", null, null));
            assertEquals(302, r.statusCode());
            assertEquals("http://evil.invalid/", r.header("location"));
            assertEquals(1, server.requests().size());
        }
    }

    @Test
    void timeoutBecomesTransportException() {
        try (TestServer server = TestServer.start(Stub.json(200, "{}").delayed(1500))) {
            TransportException e =
                    assertThrows(
                            TransportException.class,
                            () ->
                                    transport.send(
                                            post(
                                                    server.baseUri(),
                                                    "/slow",
                                                    null,
                                                    Duration.ofMillis(200))));
            assertTrue(e.getMessage().contains("/slow"));
        }
    }

    @Test
    void connectionRefusedBecomesTransportException() {
        URI dead;
        try (TestServer server = TestServer.start(Stub.json(200, "{}"))) {
            dead = server.baseUri();
        }
        assertThrows(TransportException.class, () -> transport.send(post(dead, "/x", null, null)));
    }

    @Test
    void interruptionIsPreservedAndReportedAsTransportException() {
        try (TestServer server = TestServer.start(Stub.json(200, "{}").delayed(1500))) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(
                        TransportException.class,
                        () -> transport.send(post(server.baseUri(), "/x", null, null)));
                assertTrue(
                        Thread.currentThread().isInterrupted(), "interrupt flag must be restored");
            } finally {
                Thread.interrupted(); // clear for other tests
            }
        }
    }

    @Test
    void asyncSuccessAndFailure() throws Exception {
        try (TestServer server = TestServer.start(Stub.json(201, "{}"))) {
            assertEquals(
                    201,
                    transport
                            .sendAsync(post(server.baseUri(), "/x", "{}", null))
                            .get()
                            .statusCode());
        }
        URI dead;
        try (TestServer server = TestServer.start(Stub.json(200, "{}"))) {
            dead = server.baseUri();
        }
        var future = transport.sendAsync(post(dead, "/x", null, null));
        CompletionException e = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(TransportException.class, e.getCause());
    }
}
