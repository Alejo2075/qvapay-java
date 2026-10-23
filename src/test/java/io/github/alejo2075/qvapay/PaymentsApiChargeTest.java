package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.exception.NotFoundException;
import io.github.alejo2075.qvapay.exception.RateLimitException;
import io.github.alejo2075.qvapay.exception.ServerException;
import io.github.alejo2075.qvapay.exception.TransportException;
import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.http.RetryPolicy;
import io.github.alejo2075.qvapay.model.ChargeRequest;
import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import io.github.alejo2075.qvapay.testsupport.TestServer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** None of these tests touches the network or any real account: transports are fakes. */
class PaymentsApiChargeTest {

    private static final ChargeRequest REQ =
            new ChargeRequest(
                    new BigDecimal("10.00"), "uuid-del-usuario", "Suscripción mensual", "sub-001");
    private static final RetryPolicy AGGRESSIVE =
            RetryPolicy.exponentialBackoff(5, Duration.ofMillis(1), Duration.ofMillis(2));

    private static QvaPayClient retrying(FakeTransport t) {
        return QvaPayClient.builder()
                .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                .transport(t)
                .retryPolicy(AGGRESSIVE)
                .build();
    }

    @Test
    void chargePostsTheDocumentedBodyAndParsesTheResponse() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/charge.json")));
        var r = Clients.withApp(t).payments().charge(REQ);

        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/charge", req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        assertEquals(Clients.APP_SECRET, req.headers().get("app-secret"));
        assertEquals(
                "{\"amount\":10.00,\"user_uuid\":\"uuid-del-usuario\",\"description\":\"Suscripción mensual\",\"remote_id\":\"sub-001\"}",
                new String(req.body(), StandardCharsets.UTF_8));

        assertTrue(r.success());
        assertEquals("Se ha aplicado el cargo correctamente", r.message());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", r.transaction().uuid());
        assertEquals(new BigDecimal("10.00"), r.transaction().amount());
        assertEquals(TransactionStatus.PAID, r.transaction().status());
        assertNull(r.transaction().products());
        assertNull(r.transaction().paidBy());
    }

    @Test
    void documentedErrorsAreTyped() {
        assertThrows(
                ValidationException.class,
                () ->
                        Clients.withApp(
                                        FakeTransport.returning(
                                                FakeTransport.json(
                                                        400,
                                                        "{\"error\":\"Insufficient balance\"}")))
                                .payments()
                                .charge(REQ));
        assertThrows(
                NotFoundException.class,
                () ->
                        Clients.withApp(
                                        FakeTransport.returning(
                                                FakeTransport.json(
                                                        404, "{\"error\":\"User not found\"}")))
                                .payments()
                                .charge(REQ));
        assertThrows(
                RateLimitException.class,
                () ->
                        Clients.withApp(
                                        FakeTransport.returning(
                                                FakeTransport.json(
                                                        429, "{\"error\":\"Too many\"}")))
                                .payments()
                                .charge(REQ));
    }

    @Test
    void chargeIsSentExactlyOnceOnServerErrorRateLimitAndNetworkErrorEvenWithRetriesEnabled() {
        for (Object failure :
                new Object[] {
                    FakeTransport.json(500, "{}"),
                    FakeTransport.json(503, "{}"),
                    FakeTransport.json(
                            429, "{}", java.util.Map.of("Retry-After", java.util.List.of("0"))),
                    FakeTransport.networkError()
                }) {
            FakeTransport t =
                    FakeTransport.returning(
                            failure,
                            FakeTransport.json(200, Fixtures.load("merchant/charge.json")));
            assertThrows(RuntimeException.class, () -> retrying(t).payments().charge(REQ));
            assertEquals(1, t.requests().size(), "charge must not be retried after " + failure);
        }
    }

    @Test
    void asyncChargeIsAlsoSentExactlyOnce() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"),
                        FakeTransport.json(200, Fixtures.load("merchant/charge.json")));
        var f = retrying(t).payments().chargeAsync(REQ);
        var e = assertThrows(java.util.concurrent.ExecutionException.class, f::get);
        assertTrue(e.getCause() instanceof ServerException);
        assertEquals(1, t.requests().size());
    }

    @Test
    void aRealTimeoutIsNotRetriedAndLeavesTheOutcomeUnknown() throws Exception {
        try (TestServer server =
                TestServer.start(
                        TestServer.Stub.json(200, Fixtures.load("merchant/charge.json"))
                                .delayed(3000))) {
            QvaPayClient client =
                    QvaPayClient.builder()
                            .baseUrl(server.baseUri().toString())
                            .allowInsecureBaseUrlForTesting()
                            .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                            .requestTimeout(Duration.ofMillis(600))
                            .retryPolicy(AGGRESSIVE)
                            .build();
            assertThrows(TransportException.class, () -> client.payments().charge(REQ));
            // A retry would have reached the server within a few milliseconds of the failure.
            // On a very slow machine the first request may not even have arrived: never more than
            // one.
            Thread.sleep(500);
            assertTrue(
                    server.requests().size() <= 1,
                    "charge must never be sent twice, saw " + server.requests().size());
        }
    }

    @Test
    void localValidationRejectsBadChargesBeforeAnyRequest() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChargeRequest(BigDecimal.ZERO, "u", "d", "r"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChargeRequest(new BigDecimal("100000.01"), "u", "d", "r"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChargeRequest(BigDecimal.ONE, " ", "d", "r"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChargeRequest(BigDecimal.ONE, "u", "", "r"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ChargeRequest(BigDecimal.ONE, "u", "d", null));
        new ChargeRequest(new BigDecimal("100000"), "u", "d", "r");
    }
}
