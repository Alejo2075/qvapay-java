package example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.alejo2075.qvapay.webhook.InvoiceWebhookEvent;
import io.github.alejo2075.qvapay.webhook.Webhooks;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookServerTest {

    private static final String SECRET = "test-secret-not-real";
    private static final byte[] BODY = ("{\"transaction_uuid\":\"7c9e6679-7425-40de-944b-e07fc1f90ae7\",\"remote_id\":\"order-12345\","
            + "\"status\":\"paid\",\"amount\":\"5.00\",\"signed\":0}").getBytes(StandardCharsets.UTF_8);

    private final List<InvoiceWebhookEvent> paid = new CopyOnWriteArrayList<>();
    private WebhookServer server;

    @BeforeEach
    void start() throws Exception {
        server = new WebhookServer(0, SECRET, paid::add);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private int post(String signature, String timestamp) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/qvapay/webhook"))
                .POST(HttpRequest.BodyPublishers.ofByteArray(BODY))
                .header("Content-Type", "application/json");
        if (signature != null) {
            b.header("x-qvapay-signature", signature);
        }
        if (timestamp != null) {
            b.header("x-qvapay-timestamp", timestamp);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String now() {
        return Long.toString(Instant.now().getEpochSecond());
    }

    @Test
    void acceptsAValidDeliveryAndHandlesItOnlyOnce() throws Exception {
        String sig = Webhooks.sign(BODY, SECRET);
        assertEquals(200, post(sig, now()));
        assertEquals(200, post(sig, now()));
        assertEquals(1, paid.size());
        assertEquals("order-12345", paid.get(0).remoteId());
    }

    @Test
    void rejectsUnsignedTamperedAndStaleDeliveries() throws Exception {
        assertEquals(401, post(null, now()));
        assertEquals(401, post(Webhooks.sign(BODY, "another-secret"), now()));
        assertEquals(401, post(Webhooks.sign(BODY, SECRET), "1"));
        assertEquals(0, paid.size());
    }
}
