package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.http.RetryPolicy;
import io.github.alejo2075.qvapay.model.AppTransaction;
import io.github.alejo2075.qvapay.model.AppTransactionPageResponse;
import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionApiListTest {

    private static String body(FakeTransport t) {
        return new String(t.lastRequest().body(), StandardCharsets.UTF_8);
    }

    @Test
    void listPostsPageAndTakeInTheBodyAndParsesTheTransactionsWrapper() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/transactions.json")));
        AppTransactionPageResponse page = Clients.withApp(t).transactions().list(2, 50);

        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/transactions", req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        assertEquals("{\"page\":2,\"take\":50}", body(t));

        assertEquals("APP Transactions", page.message());
        assertEquals(1, page.transactions().size());
        AppTransaction tx = page.transactions().get(0);
        assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", tx.uuid());
        assertEquals(new BigDecimal("25.00"), tx.amount());
        assertEquals("order-12345", tx.remoteId());
        assertEquals(TransactionStatus.PAID, tx.status());
        assertEquals(Instant.parse("2024-06-20T14:30:00Z"), tx.createdAt());
        assertEquals("Producto 1", tx.products().get(0).name());
        assertEquals(new BigDecimal("25.00"), tx.products().get(0).price());
        assertEquals(new BigDecimal("1"), tx.products().get(0).quantity());
    }

    @Test
    void defaultListUsesDocumentedDefaults() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(
                                200, "{\"message\":\"APP Transactions\",\"transactions\":[]}"));
        AppTransactionPageResponse page = Clients.withApp(t).transactions().list();
        assertEquals("{\"page\":1,\"take\":30}", body(t));
        assertTrue(page.transactions().isEmpty());
    }

    @Test
    void missingListBecomesEmpty() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{\"message\":\"x\"}"));
        assertTrue(Clients.withApp(t).transactions().list().transactions().isEmpty());
    }

    @Test
    void statusesAreParsedTolerantly() {
        String json =
                "{\"transactions\":[{\"status\":\"pending\"},{\"status\":\"CANCELLED\"},{\"status\":\"refunded\"},{\"status\":null},{}]}";
        var txs =
                Clients.withApp(FakeTransport.returning(FakeTransport.json(200, json)))
                        .transactions()
                        .list()
                        .transactions();
        assertEquals(TransactionStatus.PENDING, txs.get(0).status());
        assertEquals(TransactionStatus.CANCELLED, txs.get(1).status());
        assertEquals(TransactionStatus.UNKNOWN, txs.get(2).status());
        assertEquals(null, txs.get(3).status());
        assertEquals(null, txs.get(4).status());
    }

    @Test
    void amountsMayBeDecimalStrings() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, "{\"transactions\":[{\"amount\":\"12.50\"}]}"));
        assertEquals(
                new BigDecimal("12.50"),
                Clients.withApp(t).transactions().list().transactions().get(0).amount());
    }

    @Test
    void pageAndTakeAreValidatedAgainstDocumentedLimits() {
        var tx =
                Clients.withApp(FakeTransport.returning(FakeTransport.json(200, "{}")))
                        .transactions();
        assertThrows(IllegalArgumentException.class, () -> tx.list(0, 30));
        assertThrows(IllegalArgumentException.class, () -> tx.list(1, 0));
        assertThrows(IllegalArgumentException.class, () -> tx.list(1, 101));
        tx.list(1, 100);
        tx.list(1, 1);
    }

    @Test
    void invalidArgumentsFailTheAsyncFutureWithoutSendingAnything() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        var f = Clients.withApp(t).transactions().listAsync(0, 10);
        assertTrue(f.isCompletedExceptionally());
        assertTrue(t.requests().isEmpty());
    }

    @Test
    void asyncForm() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/transactions.json")));
        assertEquals(
                1, Clients.withApp(t).transactions().listAsync(1, 30).get().transactions().size());
    }

    @Test
    void readOnlyListMayBeRetriedWhenTheUserOptsIn() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"),
                        FakeTransport.json(200, Fixtures.load("merchant/transactions.json")));
        var client =
                QvaPayClient.builder()
                        .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                        .transport(t)
                        .retryPolicy(
                                RetryPolicy.exponentialBackoff(
                                        3, Duration.ofMillis(1), Duration.ofMillis(2)))
                        .build();
        assertEquals(1, client.transactions().list().transactions().size());
        assertEquals(2, t.requests().size());
    }
}
