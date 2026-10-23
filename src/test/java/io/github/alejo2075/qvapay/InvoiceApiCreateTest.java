package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alejo2075.qvapay.exception.QvaPayException;
import io.github.alejo2075.qvapay.exception.ServerException;
import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.http.RetryPolicy;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.Invoice;
import io.github.alejo2075.qvapay.model.InvoiceProduct;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceApiCreateTest {

    private static final Instant FAR = Instant.parse("2999-12-31T23:59:59Z");

    private static CreateInvoiceRequest.Builder valid() {
        return CreateInvoiceRequest.builder()
                .amount(new BigDecimal("25.00"))
                .description("Compra de producto digital")
                .remoteId("order-12345");
    }

    @Test
    void createSendsTheDocumentedBodyWithExactAmount() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        Invoice invoice =
                Clients.withApp(t)
                        .invoices()
                        .create(
                                valid().webhook("https://mitienda.com/webhook/qvapay")
                                        .product(
                                                InvoiceProduct.of(
                                                        "Producto 1", new BigDecimal("15.00"), 1))
                                        .product(
                                                InvoiceProduct.of(
                                                        "Producto 2", new BigDecimal("10.00"), 1))
                                        .expireAt(FAR)
                                        .build());

        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/create_invoice", req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        assertEquals(Clients.APP_SECRET, req.headers().get("app-secret"));
        assertEquals(
                Fixtures.load("merchant/create-invoice-request.json").trim(),
                new String(req.body(), StandardCharsets.UTF_8));

        assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", invoice.transactionUuid());
        assertEquals(new BigDecimal("25.00"), invoice.amount());
        assertEquals("order-12345", invoice.remoteId());
        assertEquals(
                "https://www.qvapay.com/pay/7c9e6679-7425-40de-944b-e07fc1f90ae7", invoice.url());
        assertEquals(Instant.parse("2024-12-31T23:59:59Z"), invoice.expireAt());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", invoice.appId());
    }

    @Test
    void optionalFieldsAreOmittedWhenNotSet() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        Clients.withApp(t).invoices().create(valid().build());
        assertEquals(
                "{\"amount\":25.00,\"description\":\"Compra de producto digital\",\"remote_id\":\"order-12345\"}",
                new String(t.lastRequest().body(), StandardCharsets.UTF_8));
    }

    @Test
    void expireAtMayBeNullInTheResponse() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(
                                200,
                                "{\"transaction_uuid\":\"u\",\"expire_at\":null,\"amount\":\"3.50\"}"));
        Invoice i = Clients.withApp(t).invoices().create(valid().build());
        assertNull(i.expireAt());
        assertEquals(new BigDecimal("3.50"), i.amount());
    }

    @Test
    void asyncFormSendsTheSameRequest() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        Invoice i = Clients.withApp(t).invoices().createAsync(valid().build()).get();
        assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", i.transactionUuid());
        assertEquals("/v2/create_invoice", t.lastRequest().uri().getPath());
    }

    @Test
    void invalidDataFromTheServerIsAValidationException() {
        FakeTransport t =
                FakeTransport.returning(FakeTransport.json(400, "{\"error\":\"Invalid amount\"}"));
        ValidationException e =
                assertThrows(
                        ValidationException.class,
                        () -> Clients.withApp(t).invoices().create(valid().build()));
        assertEquals("Invalid amount", e.apiMessage());
    }

    @Test
    void createIsNeverRetriedEvenWithRetryPolicy() {
        for (Object failure :
                new Object[] {
                    FakeTransport.json(500, "{}"),
                    FakeTransport.json(429, "{}"),
                    FakeTransport.networkError()
                }) {
            FakeTransport t =
                    FakeTransport.returning(
                            failure,
                            FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
            var client =
                    io.github.alejo2075.qvapay.QvaPayClient.builder()
                            .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                            .transport(t)
                            .retryPolicy(
                                    RetryPolicy.exponentialBackoff(
                                            3, Duration.ofMillis(1), Duration.ofMillis(2)))
                            .build();
            assertThrows(QvaPayException.class, () -> client.invoices().create(valid().build()));
            assertEquals(1, t.requests().size());
        }
        assertThrows(
                ServerException.class,
                () ->
                        Clients.withApp(FakeTransport.returning(FakeTransport.json(500, "{}")))
                                .invoices()
                                .create(valid().build()));
    }

    @Test
    void localValidationFollowsTheDocumentedLimits() {
        assertThrows(IllegalArgumentException.class, () -> valid().amount(BigDecimal.ZERO).build());
        assertThrows(
                IllegalArgumentException.class, () -> valid().amount(new BigDecimal("-1")).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().amount(new BigDecimal("100000.01")).build());
        valid().amount(new BigDecimal("100000")).build();
        valid().amount(new BigDecimal("0.01")).build();
        assertThrows(IllegalArgumentException.class, () -> valid().amount(null).build());
        assertThrows(IllegalArgumentException.class, () -> valid().description("").build());
        assertThrows(
                IllegalArgumentException.class, () -> valid().description("x".repeat(256)).build());
        valid().description("x".repeat(255)).build();
        assertThrows(IllegalArgumentException.class, () -> valid().remoteId(" ").build());
        assertThrows(
                IllegalArgumentException.class, () -> valid().remoteId("x".repeat(256)).build());
        assertThrows(IllegalArgumentException.class, () -> valid().webhook("not a url").build());
        assertThrows(
                IllegalArgumentException.class, () -> valid().webhook("ftp://x.test/a").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().expireAt(Instant.parse("2000-01-01T00:00:00Z")).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().product(InvoiceProduct.of("", BigDecimal.ONE)).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().product(InvoiceProduct.of("a", new BigDecimal("-1"))).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> valid().product(InvoiceProduct.of("a", BigDecimal.ONE, 0)).build());
    }

    @Test
    void amountIsNeverRounded() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        Clients.withApp(t).invoices().create(valid().amount(new BigDecimal("0.123456789")).build());
        assertEquals(
                true,
                new String(t.lastRequest().body(), StandardCharsets.UTF_8)
                        .contains("\"amount\":0.123456789"));
    }

    @Test
    void productWithoutQuantityOmitsTheField() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        Clients.withApp(t)
                .invoices()
                .create(valid().product(InvoiceProduct.of("A", new BigDecimal("5.00"))).build());
        assertEquals(
                true,
                new String(t.lastRequest().body(), StandardCharsets.UTF_8)
                        .contains("\"products\":[{\"name\":\"A\",\"price\":5.00}]"));
    }
}
