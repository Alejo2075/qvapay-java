package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.github.alejo2075.qvapay.QvaPayClient;
import io.github.alejo2075.qvapay.exception.AuthenticationException;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.Invoice;
import io.github.alejo2075.qvapay.webhook.Webhooks;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Uses only the public, installed SDK jar and a local mock server. No QvaPay account and no money
 * are involved.
 */
class ExamplesAgainstMockServerTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAppId = new AtomicReference<>();
    private int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAppId.set(ex.getRequestHeaders().getFirst("app-id"));
            String path = ex.getRequestURI().getPath();
            String json;
            if (status != 200) {
                json = "{\"error\":\"Invalid credentials\"}";
            } else if (path.equals("/v2/balance")) {
                json = "{\"balance\":150.75}";
            } else if (path.equals("/v2/create_invoice")) {
                json = "{\"app_id\":\"a\",\"amount\":25.00,\"description\":\"Test invoice\",\"remote_id\":\"order-12345\","
                        + "\"transaction_uuid\":\"7c9e6679-7425-40de-944b-e07fc1f90ae7\",\"expire_at\":null,"
                        + "\"url\":\"https://www.qvapay.com/pay/7c9e6679-7425-40de-944b-e07fc1f90ae7\"}";
            } else if (path.equals("/v2/authorize_payments")) {
                json = "{\"message\":\"Authorize payments\",\"url\":\"https://qvapay.com/authorize_payments?app_uuid=a\"}";
            } else if (path.equals("/v2/charge")) {
                json = "{\"success\":true,\"message\":\"ok\",\"transaction\":{\"uuid\":\"t\",\"amount\":10.00,\"status\":\"paid\"}}";
            } else if (path.startsWith("/v2/transactions/")) {
                json = "{\"message\":\"APP Transaction\",\"transaction\":{\"uuid\":\"u\",\"status\":\"paid\"}}";
            } else {
                json = "{\"uuid\":\"a\",\"name\":\"Mi Tienda\"}";
            }
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private QvaPayClient client() {
        return QvaPayClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .allowInsecureBaseUrlForTesting() // only because the mock server has no TLS
                .appCredentials("test-app-id", "test-secret-not-real")
                .build();
    }

    @Test
    void createsAnInvoiceLikeTheQuickStart() {
        Invoice invoice = client().invoices().create(CreateInvoiceRequest.builder()
                .amount(new BigDecimal("25.00"))
                .description("Test invoice")
                .remoteId("order-12345")
                .build());
        assertEquals("https://www.qvapay.com/pay/7c9e6679-7425-40de-944b-e07fc1f90ae7", invoice.url());
        assertEquals("test-app-id", lastAppId.get());
        assertTrue(lastBody.get().contains("\"amount\":25.00"));
        assertFalse(lastBody.get().contains("secret"), "credentials travel in headers, never in the body");
    }

    @Test
    void readsBalanceAndConfirmsPayment() throws Exception {
        QvaPayClient client = client();
        assertEquals(new BigDecimal("150.75"), client.merchant().balance().balance());
        assertEquals("Balance: 150.75 USD", AdvancedUsage.balanceText(client).get());
        assertTrue(AdvancedUsage.isPaid(client, "7c9e6679-7425-40de-944b-e07fc1f90ae7"));
    }

    @Test
    void mapsErrorsToTypedExceptions() {
        status = 401;
        assertThrows(AuthenticationException.class, () -> client().merchant().info());
        assertEquals("Wrong app-id/app-secret", AdvancedUsage.describeFailure(client()));
    }

    @Test
    void verifiesASignedWebhookFromRawBytes() {
        byte[] body = ("{\"transaction_uuid\":\"7c9e6679\",\"remote_id\":\"order-12345\",\"status\":\"paid\",\"amount\":\"25.00\"}")
                .getBytes(StandardCharsets.UTF_8);
        String secret = "test-secret-not-real";
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        var verifier = io.github.alejo2075.qvapay.webhook.WebhookVerifier.forSecret(secret);
        var event = verifier.verifyInvoice(body, Webhooks.sign(body, secret), timestamp).event();
        assertEquals("order-12345", event.remoteId());
        assertEquals(new BigDecimal("25.00"), event.amount());
    }

    @Test
    void authorizesAndChargesAgainstTheMockOnly() {
        QvaPayClient client = client();
        assertTrue(PaymentsExample.consentUrl(client).startsWith("https://qvapay.com/authorize_payments"));
        assertTrue(lastBody.get().contains("\"remote_id\":\"user-001\""));
        var result = PaymentsExample.charge(client, "user-uuid");
        assertTrue(result.success());
        assertEquals(new BigDecimal("10.00"), result.transaction().amount());
        assertTrue(lastBody.get().contains("\"user_uuid\":\"user-uuid\""));
    }
}
