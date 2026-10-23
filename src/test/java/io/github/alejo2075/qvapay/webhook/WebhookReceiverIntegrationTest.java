package io.github.alejo2075.qvapay.webhook;

import static io.github.alejo2075.qvapay.webhook.WebhooksTest.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/** A minimal receiver showing the intended flow: raw bytes, verify, dedupe, then act. */
class WebhookReceiverIntegrationTest {

    @Test
    void receiverVerifiesRawBytesAndDeduplicates() throws Exception {
        WebhookVerifier verifier = WebhookVerifier.forSecret(SECRET);
        Set<String> processed = ConcurrentHashMap.newKeySet();
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/webhook",
                ex -> {
                    byte[] raw = ex.getRequestBody().readAllBytes();
                    int status;
                    try {
                        var v =
                                verifier.verifyInvoice(
                                        raw,
                                        ex.getRequestHeaders().getFirst(Webhooks.SIGNATURE_HEADER),
                                        ex.getRequestHeaders().getFirst(Webhooks.TIMESTAMP_HEADER));
                        processed.add(v.event().transactionUuid());
                        status = 200;
                    } catch (WebhookVerificationException e) {
                        status = 401;
                    }
                    ex.sendResponseHeaders(status, -1);
                    ex.close();
                });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhook");
            byte[] body =
                    Fixtures.load("webhook/invoice-paid.json").getBytes(StandardCharsets.UTF_8);
            String now = Long.toString(Instant.now().getEpochSecond());
            HttpClient http = HttpClient.newHttpClient();

            assertEquals(200, post(http, uri, body, Webhooks.sign(body, SECRET), now));
            assertEquals(
                    200,
                    post(http, uri, body, Webhooks.sign(body, SECRET), now),
                    "redelivery is accepted");
            assertEquals(1, processed.size(), "but handled once, keyed by transaction_uuid");

            assertEquals(401, post(http, uri, body, Webhooks.sign(body, "wrong-secret"), now));
            assertEquals(
                    401,
                    post(http, uri, body, Webhooks.sign(body, SECRET), "1"),
                    "stale timestamp");
            assertEquals(401, post(http, uri, body, null, now), "unsigned");
        } finally {
            server.stop(0);
        }
    }

    private static int post(
            HttpClient http, URI uri, byte[] body, String signature, String timestamp)
            throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(uri)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .header("Content-Type", "application/json")
                        .header(Webhooks.TIMESTAMP_HEADER, timestamp);
        if (signature != null) {
            b.header(Webhooks.SIGNATURE_HEADER, signature);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
