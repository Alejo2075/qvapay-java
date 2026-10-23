package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.exception.TransportException;
import io.github.alejo2075.qvapay.http.RetryPolicy;
import io.github.alejo2075.qvapay.testsupport.TestServer;
import io.github.alejo2075.qvapay.testsupport.TestServer.Stub;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Behaviour of whole clients over the real JDK transport. */
class ClientResilienceTest {

    private static QvaPayClient.Builder client(TestServer s) {
        return QvaPayClient.builder()
                .baseUrl(s.baseUri().toString())
                .allowInsecureBaseUrlForTesting()
                .appCredentials("app", "secret");
    }

    @Test
    void synchronousCallsRespectTheRequestTimeout() {
        try (TestServer s = TestServer.start(Stub.json(200, "{}").delayed(6000))) {
            long start = System.nanoTime();
            assertThrows(
                    TransportException.class,
                    () ->
                            client(s)
                                    .requestTimeout(Duration.ofMillis(500))
                                    .build()
                                    .merchant()
                                    .info());
            assertTrue(
                    Duration.ofNanos(System.nanoTime() - start).toMillis() < 4500,
                    "must give up at the timeout, not wait for the response");
        }
    }

    @Test
    void interruptingACallingThreadAbortsItAndKeepsTheFlag() throws Exception {
        try (TestServer s = TestServer.start(Stub.json(200, "{}").delayed(2000))) {
            QvaPayClient c = client(s).build();
            AtomicInteger outcome =
                    new AtomicInteger(); // 1 = TransportException with interrupt flag
            Thread t =
                    new Thread(
                            () -> {
                                try {
                                    c.merchant().info();
                                } catch (TransportException e) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        outcome.set(1);
                                    }
                                }
                            });
            t.start();
            Thread.sleep(300);
            t.interrupt();
            t.join(1500);
            assertEquals(false, t.isAlive(), "thread must return promptly after the interrupt");
            assertEquals(1, outcome.get());
        }
    }

    @Test
    void retryAfterIsHonouredForOptInReadsOverRealHttp() {
        AtomicInteger calls = new AtomicInteger();
        try (TestServer s =
                TestServer.start(
                        r ->
                                calls.incrementAndGet() == 1
                                        ? Stub.of(
                                                429,
                                                java.util.Map.of(
                                                        "Retry-After",
                                                        "1",
                                                        "Content-Type",
                                                        "application/json"),
                                                "{\"error\":\"slow down\"}")
                                        : Stub.json(200, "{\"balance\":3.5}"))) {
            QvaPayClient c =
                    client(s)
                            .retryPolicy(
                                    RetryPolicy.exponentialBackoff(
                                            2, Duration.ofMillis(10), Duration.ofSeconds(2)))
                            .build();
            long start = System.nanoTime();
            assertEquals(new java.math.BigDecimal("3.5"), c.merchant().balance().balance());
            assertTrue(
                    Duration.ofNanos(System.nanoTime() - start).toMillis() >= 900,
                    "waits about Retry-After");
            assertEquals(2, s.requests().size());
        }
    }

    @Test
    void withoutOptInA429IsReportedImmediately() {
        try (TestServer s =
                TestServer.start(
                        Stub.of(
                                429,
                                java.util.Map.of(
                                        "Retry-After", "1", "Content-Type", "application/json"),
                                "{\"error\":\"slow down\"}"))) {
            var e =
                    assertThrows(
                            io.github.alejo2075.qvapay.exception.RateLimitException.class,
                            () -> client(s).build().merchant().balance());
            assertEquals(Duration.ofSeconds(1), e.retryAfter().orElseThrow());
            assertEquals(1, s.requests().size());
        }
    }

    @Test
    void pathSegmentsCannotInjectQueryOrTraversal() {
        try (TestServer s = TestServer.start(Stub.json(200, "{}"))) {
            client(s).build().transactions().get("../../auth/login?x=1 &y=2");
            var r = s.requests().get(0);
            assertEquals("/v2/transactions/..%2F..%2Fauth%2Flogin%3Fx%3D1%20%26y%3D2", r.rawPath());
            assertEquals(null, r.query());
        }
    }
}
