package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alejo2075.qvapay.exception.NotFoundException;
import io.github.alejo2075.qvapay.model.AppTransactionDetailResponse;
import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransactionApiGetTest {

    private static final String UUID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    @Test
    void getPostsToTheUuidPathWithAppHeadersAndNoBodyFields() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/transaction-detail.json")));
        AppTransactionDetailResponse r = Clients.withApp(t).transactions().get(UUID);

        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/transactions/" + UUID, req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        assertEquals("{}", new String(req.body()));

        assertEquals("APP Transaction", r.message());
        assertEquals(UUID, r.transaction().uuid());
        assertEquals(TransactionStatus.PAID, r.transaction().status());
        assertEquals(new BigDecimal("25.00"), r.transaction().amount());
    }

    @Test
    void paidByKeepsItsCapitalizedWireNameAndAllFields() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/transaction-detail.json")));
        var payer = Clients.withApp(t).transactions().get(UUID).transaction().paidBy();
        assertEquals("user-uuid", payer.uuid());
        assertEquals("Juan", payer.name());
        assertEquals("juan123", payer.username());
        assertEquals("Pérez", payer.lastname());
        assertEquals("avatars/juan.jpg", payer.image());
        assertFalse(payer.vip());
        assertEquals("verified", payer.kyc());
        assertFalse(payer.goldenCheck());
    }

    @Test
    void paidByIsNullWhenNotPaid() {
        String json =
                "{\"message\":\"APP Transaction\",\"transaction\":{\"uuid\":\"u\",\"status\":\"pending\",\"PaidBy\":null}}";
        assertNull(
                Clients.withApp(FakeTransport.returning(FakeTransport.json(200, json)))
                        .transactions()
                        .get("u")
                        .transaction()
                        .paidBy());
        String noKey =
                "{\"message\":\"APP Transaction\",\"transaction\":{\"uuid\":\"u\",\"status\":\"pending\"}}";
        assertNull(
                Clients.withApp(FakeTransport.returning(FakeTransport.json(200, noKey)))
                        .transactions()
                        .get("u")
                        .transaction()
                        .paidBy());
    }

    @Test
    void uuidIsPercentEncodedAndCannotEscapeThePath() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        Clients.withApp(t).transactions().get("../balance?x=1#frag/ü");
        assertEquals(
                "/v2/transactions/..%2Fbalance%3Fx%3D1%23frag%2F%C3%BC",
                t.lastRequest().uri().getRawPath());
        assertNull(t.lastRequest().uri().getRawQuery());
    }

    @Test
    void notFoundIsTyped() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(404, "{\"error\":\"Transaction not found\"}"));
        assertThrows(NotFoundException.class, () -> Clients.withApp(t).transactions().get(UUID));
    }

    @Test
    void blankUuidIsRejectedLocally() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        var api = Clients.withApp(t).transactions();
        assertThrows(IllegalArgumentException.class, () -> api.get(" "));
        assertThrows(IllegalArgumentException.class, () -> api.get(null));
        assertEquals(true, api.getAsync("").isCompletedExceptionally());
        assertEquals(0, t.requests().size());
    }

    @Test
    void asyncForm() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/transaction-detail.json")));
        assertEquals(
                "Juan",
                Clients.withApp(t)
                        .transactions()
                        .getAsync(UUID)
                        .get()
                        .transaction()
                        .paidBy()
                        .name());
    }
}
