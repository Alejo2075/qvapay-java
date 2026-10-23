package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alejo2075.qvapay.exception.NotFoundException;
import io.github.alejo2075.qvapay.exception.PermissionDeniedException;
import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.model.Invoice;
import io.github.alejo2075.qvapay.model.InvoiceProduct;
import io.github.alejo2075.qvapay.model.ModifyInvoiceRequest;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceApiModifyTest {

    private static final String UUID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private String modifyBody(ModifyInvoiceRequest r) {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        Invoice i = Clients.withApp(t).invoices().modify(r);
        assertEquals(UUID, i.transactionUuid());
        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/modify_invoice", req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        return new String(req.body(), StandardCharsets.UTF_8);
    }

    @Test
    void onlyChangedFieldsAreSent() {
        assertEquals(
                "{\"transaction_uuid\":\"" + UUID + "\"}",
                modifyBody(ModifyInvoiceRequest.builder(UUID).build()));
        assertEquals(
                "{\"transaction_uuid\":\"" + UUID + "\",\"amount\":30.00}",
                modifyBody(
                        ModifyInvoiceRequest.builder(UUID)
                                .amount(new BigDecimal("30.00"))
                                .build()));
    }

    @Test
    void absentExpireAtIsNotSent() {
        String body =
                modifyBody(
                        ModifyInvoiceRequest.builder(UUID)
                                .description("Compra actualizada")
                                .build());
        assertEquals(false, body.contains("expire_at"));
    }

    @Test
    void resetExpireAtSendsExplicitJsonNull() {
        String body = modifyBody(ModifyInvoiceRequest.builder(UUID).resetExpireAt().build());
        assertEquals("{\"transaction_uuid\":\"" + UUID + "\",\"expire_at\":null}", body);
    }

    @Test
    void explicitExpireAtIsFormattedAsIsoWithMillis() {
        String body =
                modifyBody(
                        ModifyInvoiceRequest.builder(UUID)
                                .expireAt(Instant.parse("2999-01-31T23:59:59Z"))
                                .build());
        assertEquals(
                "{\"transaction_uuid\":\""
                        + UUID
                        + "\",\"expire_at\":\"2999-01-31T23:59:59.000Z\"}",
                body);
    }

    @Test
    void lastCallWinsBetweenResetAndExpireAt() {
        ModifyInvoiceRequest r =
                ModifyInvoiceRequest.builder(UUID)
                        .expireAt(Instant.parse("2999-01-31T23:59:59Z"))
                        .resetExpireAt()
                        .build();
        assertEquals(true, r.expireAtSpecified());
        assertEquals(null, r.expireAt());
        ModifyInvoiceRequest r2 =
                ModifyInvoiceRequest.builder(UUID)
                        .resetExpireAt()
                        .expireAt(Instant.parse("2999-01-31T23:59:59Z"))
                        .build();
        assertEquals(Instant.parse("2999-01-31T23:59:59Z"), r2.expireAt());
    }

    @Test
    void emptyWebhookClearsItAndEmptyProductsAreSent() {
        String body =
                modifyBody(
                        ModifyInvoiceRequest.builder(UUID).webhook("").products(List.of()).build());
        assertEquals(
                "{\"transaction_uuid\":\"" + UUID + "\",\"webhook\":\"\",\"products\":[]}", body);
    }

    @Test
    void fullModificationMatchesTheDocumentedBody() {
        String body =
                modifyBody(
                        ModifyInvoiceRequest.builder(UUID)
                                .amount(new BigDecimal("30.00"))
                                .description("Compra actualizada")
                                .remoteId("order-12345-v2")
                                .webhook("https://mitienda.com/webhook/qvapay")
                                .products(
                                        List.of(
                                                InvoiceProduct.of(
                                                        "Producto actualizado",
                                                        new BigDecimal("30.00"),
                                                        1)))
                                .expireAt(Instant.parse("2999-01-31T23:59:59Z"))
                                .build());
        assertEquals(
                "{\"transaction_uuid\":\""
                        + UUID
                        + "\",\"amount\":30.00,\"description\":\"Compra actualizada\",\"remote_id\":\"order-12345-v2\","
                        + "\"webhook\":\"https://mitienda.com/webhook/qvapay\",\"products\":[{\"name\":\"Producto actualizado\",\"price\":30.00,\"quantity\":1}],"
                        + "\"expire_at\":\"2999-01-31T23:59:59.000Z\"}",
                body);
    }

    @Test
    void errorsMapToTypedExceptions() {
        ModifyInvoiceRequest r = ModifyInvoiceRequest.builder(UUID).amount(BigDecimal.ONE).build();
        assertThrows(
                ValidationException.class,
                () ->
                        Clients.withApp(
                                        FakeTransport.returning(
                                                FakeTransport.json(
                                                        400, "{\"error\":\"not pending\"}")))
                                .invoices()
                                .modify(r));
        assertThrows(
                PermissionDeniedException.class,
                () ->
                        Clients.withApp(
                                        FakeTransport.returning(
                                                FakeTransport.json(
                                                        403, "{\"error\":\"not yours\"}")))
                                .invoices()
                                .modify(r));
        assertThrows(
                NotFoundException.class,
                () ->
                        Clients.withApp(
                                        FakeTransport.returning(
                                                FakeTransport.json(404, "{\"error\":\"nf\"}")))
                                .invoices()
                                .modify(r));
    }

    @Test
    void modifyIsNeverRetried() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"),
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        var client =
                QvaPayClient.builder()
                        .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                        .transport(t)
                        .retryPolicy(
                                io.github.alejo2075.qvapay.http.RetryPolicy.exponentialBackoff(
                                        3,
                                        java.time.Duration.ofMillis(1),
                                        java.time.Duration.ofMillis(2)))
                        .build();
        assertThrows(
                io.github.alejo2075.qvapay.exception.ServerException.class,
                () -> client.invoices().modify(ModifyInvoiceRequest.builder(UUID).build()));
        assertEquals(1, t.requests().size());
    }

    @Test
    void asyncForm() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        assertEquals(
                UUID,
                Clients.withApp(t)
                        .invoices()
                        .modifyAsync(ModifyInvoiceRequest.builder(UUID).build())
                        .get()
                        .transactionUuid());
    }

    @Test
    void localValidation() {
        assertThrows(
                IllegalArgumentException.class, () -> ModifyInvoiceRequest.builder(" ").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ModifyInvoiceRequest.builder(UUID).amount(BigDecimal.ZERO).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ModifyInvoiceRequest.builder(UUID).description("").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ModifyInvoiceRequest.builder(UUID).remoteId("x".repeat(256)).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> ModifyInvoiceRequest.builder(UUID).webhook("nope").build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ModifyInvoiceRequest.builder(UUID)
                                .expireAt(Instant.parse("2000-01-01T00:00:00Z")));
    }
}
