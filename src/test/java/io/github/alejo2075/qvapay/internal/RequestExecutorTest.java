package io.github.alejo2075.qvapay.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.auth.AppCredentials;
import io.github.alejo2075.qvapay.auth.AuthPolicy;
import io.github.alejo2075.qvapay.auth.BearerTokenProvider;
import io.github.alejo2075.qvapay.exception.RateLimitException;
import io.github.alejo2075.qvapay.exception.SerializationException;
import io.github.alejo2075.qvapay.exception.ServerException;
import io.github.alejo2075.qvapay.exception.TransportException;
import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.http.RetryPolicy;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class RequestExecutorTest {

    private static final AppCredentials APP = AppCredentials.of("app-1", "s3cr3t");
    private static final RetryPolicy FAST_RETRY =
            RetryPolicy.exponentialBackoff(3, Duration.ofMillis(1), Duration.ofMillis(5));

    private static RequestExecutor executor(
            FakeTransport t, AppCredentials app, BearerTokenProvider bearer, RetryPolicy retry) {
        return new RequestExecutor(
                new ClientSettings(
                        t,
                        URI.create("https://api.qvapay.com"),
                        app,
                        bearer,
                        Duration.ofSeconds(9),
                        retry,
                        "qvapay-java/test",
                        Clock.systemUTC()));
    }

    private static final Operation APP_READ =
            Operation.post("/v2/balance", null, AuthPolicy.APP, true);
    private static final Operation APP_WRITE =
            Operation.post("/v2/create_invoice", Map.of("amount", 1), AuthPolicy.APP, false);

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    void appPolicySendsOnlyAppHeadersAndEmptyJsonBody() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{\"balance\":1}"));
        String r =
                executor(t, APP, BearerTokenProvider.of("tok"), RetryPolicy.disabled())
                        .execute(APP_READ, RequestExecutorTest::text);
        assertEquals("{\"balance\":1}", r);
        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/balance", req.uri().toString());
        assertEquals("app-1", req.headers().get("app-id"));
        assertEquals("s3cr3t", req.headers().get("app-secret"));
        assertNull(req.headers().get("Authorization"), "no bearer leak");
        assertEquals("application/json", req.headers().get("content-type"));
        assertEquals("application/json", req.headers().get("accept"));
        assertEquals("qvapay-java/test", req.headers().get("user-agent"));
        assertEquals("{}", text(req.body()));
        assertEquals(Duration.ofSeconds(9), req.timeout());
    }

    @Test
    void bearerPolicySendsOnlyAuthorizationHeader() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        Operation op = new Operation("GET", "/user", null, AuthPolicy.BEARER, null, true);
        executor(t, APP, BearerTokenProvider.of("tok-9"), RetryPolicy.disabled())
                .execute(op, RequestExecutorTest::text);
        var req = t.lastRequest();
        assertEquals("Bearer tok-9", req.headers().get("Authorization"));
        assertNull(req.headers().get("app-id"));
        assertNull(req.headers().get("app-secret"));
        assertNull(req.body());
        assertNull(req.headers().get("content-type"));
    }

    @Test
    void publicPolicySendsNoCredentials() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "[]"));
        Operation op = new Operation("GET", "/coins", null, AuthPolicy.PUBLIC, null, true);
        executor(t, APP, BearerTokenProvider.of("tok"), RetryPolicy.disabled())
                .execute(op, RequestExecutorTest::text);
        assertNull(t.lastRequest().headers().get("app-id"));
        assertNull(t.lastRequest().headers().get("Authorization"));
    }

    @Test
    void appOrBearerPrefersAppAndNeverSendsBoth() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        Operation op = new Operation("GET", "/p2p", null, AuthPolicy.APP_OR_BEARER, null, true);
        executor(t, APP, BearerTokenProvider.of("tok"), RetryPolicy.disabled())
                .execute(op, RequestExecutorTest::text);
        assertEquals("app-1", t.lastRequest().headers().get("app-id"));
        assertNull(t.lastRequest().headers().get("Authorization"));

        FakeTransport t2 = FakeTransport.returning(FakeTransport.json(200, "{}"));
        executor(t2, null, BearerTokenProvider.of("tok"), RetryPolicy.disabled())
                .execute(op, RequestExecutorTest::text);
        assertEquals("Bearer tok", t2.lastRequest().headers().get("Authorization"));
        assertNull(t2.lastRequest().headers().get("app-id"));
    }

    @Test
    void missingCredentialsFailBeforeAnyRequestWithoutLeakingAnything() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                executor(
                                                t,
                                                null,
                                                BearerTokenProvider.of("tok"),
                                                RetryPolicy.disabled())
                                        .execute(APP_READ, RequestExecutorTest::text));
        assertFalse(e.getMessage().contains("tok"));
        assertTrue(t.requests().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () ->
                        executor(t, APP, null, RetryPolicy.disabled())
                                .execute(
                                        new Operation(
                                                "GET",
                                                "/user",
                                                null,
                                                AuthPolicy.BEARER,
                                                null,
                                                true),
                                        RequestExecutorTest::text));
    }

    @Test
    void queryParametersAreEncoded() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        Operation op =
                new Operation(
                        "GET",
                        "/p2p",
                        new java.util.LinkedHashMap<>(Map.of("coin", "a b&c")),
                        AuthPolicy.PUBLIC,
                        null,
                        true);
        executor(t, null, null, RetryPolicy.disabled()).execute(op, RequestExecutorTest::text);
        assertEquals("https://api.qvapay.com/p2p?coin=a%20b%26c", t.lastRequest().uri().toString());
    }

    @Test
    void errorResponsesBecomeTypedExceptions() {
        FakeTransport t =
                FakeTransport.returning(FakeTransport.json(400, "{\"error\":\"bad amount\"}"));
        ValidationException e =
                assertThrows(
                        ValidationException.class,
                        () ->
                                executor(t, APP, null, RetryPolicy.disabled())
                                        .execute(APP_WRITE, RequestExecutorTest::text));
        assertEquals("bad amount", e.apiMessage());
        assertEquals(1, t.requests().size());
    }

    @Test
    void successBodyThatCannotBeParsedIsASerializationException() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "nope"));
        assertThrows(
                SerializationException.class,
                () ->
                        executor(t, APP, null, FAST_RETRY)
                                .execute(APP_READ, b -> JsonCodec.read(b, Map.class)));
        assertEquals(1, t.requests().size(), "parse errors are not retried");
    }

    @Test
    void noRetriesByDefaultEvenForReads() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"), FakeTransport.json(200, "{}"));
        assertThrows(
                ServerException.class,
                () ->
                        executor(t, APP, null, RetryPolicy.disabled())
                                .execute(APP_READ, RequestExecutorTest::text));
        assertEquals(1, t.requests().size());
    }

    @Test
    void safeReadsAreRetriedOn5xxAnd429AndNetworkErrors() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"),
                        FakeTransport.networkError(),
                        FakeTransport.json(200, "ok-body"));
        String r = executor(t, APP, null, FAST_RETRY).execute(APP_READ, RequestExecutorTest::text);
        assertEquals("ok-body", r);
        assertEquals(3, t.requests().size());
    }

    @Test
    void retriesStopAtMaxAttempts() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(500, "{}"));
        assertThrows(
                ServerException.class,
                () ->
                        executor(t, APP, null, FAST_RETRY)
                                .execute(APP_READ, RequestExecutorTest::text));
        assertEquals(3, t.requests().size());
    }

    @Test
    void clientErrorsAreNeverRetried() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(400, "{}"), FakeTransport.json(200, "{}"));
        assertThrows(
                ValidationException.class,
                () ->
                        executor(t, APP, null, FAST_RETRY)
                                .execute(APP_READ, RequestExecutorTest::text));
        assertEquals(1, t.requests().size());
    }

    @Test
    void unsafeOperationsAreNeverRetriedEvenWithRetryEnabled() {
        for (Object failure :
                List.of(
                        FakeTransport.json(500, "{}"),
                        FakeTransport.json(429, "{}"),
                        FakeTransport.networkError())) {
            FakeTransport t = FakeTransport.returning(failure, FakeTransport.json(200, "{}"));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            executor(t, APP, null, FAST_RETRY)
                                    .execute(APP_WRITE, RequestExecutorTest::text));
            assertEquals(1, t.requests().size(), "write must be sent exactly once for " + failure);
        }
    }

    @Test
    void retryAfterIsHonoured() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(429, "{}", Map.of("Retry-After", List.of("1"))),
                        FakeTransport.json(200, "fine"));
        RetryPolicy policy =
                RetryPolicy.exponentialBackoff(2, Duration.ofMillis(1), Duration.ofSeconds(2));
        long start = System.nanoTime();
        assertEquals(
                "fine",
                executor(t, APP, null, policy).execute(APP_READ, RequestExecutorTest::text));
        assertTrue(
                Duration.ofNanos(System.nanoTime() - start).toMillis() >= 900,
                "must wait about Retry-After");
    }

    @Test
    void retryAfterLongerThanMaxDelayIsSurfacedInsteadOfRetriedEarly() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(429, "{}", Map.of("Retry-After", List.of("60"))),
                        FakeTransport.json(200, "{}"));
        RateLimitException e =
                assertThrows(
                        RateLimitException.class,
                        () ->
                                executor(t, APP, null, FAST_RETRY)
                                        .execute(APP_READ, RequestExecutorTest::text));
        assertEquals(Duration.ofSeconds(60), e.retryAfter().orElseThrow());
        assertEquals(1, t.requests().size());
    }

    @Test
    void interruptedThreadsAreNotRetried() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.networkError(), FakeTransport.json(200, "{}"));
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    TransportException.class,
                    () ->
                            executor(t, APP, null, FAST_RETRY)
                                    .execute(APP_READ, RequestExecutorTest::text));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, t.requests().size());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void asyncSuccessCompletesWithMappedValue() throws Exception {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "async-ok"));
        assertEquals(
                "async-ok",
                executor(t, APP, null, RetryPolicy.disabled())
                        .executeAsync(APP_READ, RequestExecutorTest::text)
                        .get());
    }

    @Test
    void asyncFailuresCompleteWithTheTypedExceptionAsCause() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(404, "{\"error\":\"nf\"}"));
        var f =
                executor(t, APP, null, RetryPolicy.disabled())
                        .executeAsync(APP_READ, RequestExecutorTest::text);
        ExecutionException ee = assertThrows(ExecutionException.class, f::get);
        assertInstanceOf(
                io.github.alejo2075.qvapay.exception.NotFoundException.class, ee.getCause());
        CompletionException ce = assertThrows(CompletionException.class, f::join);
        assertInstanceOf(
                io.github.alejo2075.qvapay.exception.NotFoundException.class, ce.getCause());
    }

    @Test
    void asyncRetriesSafeReadsButNotWrites() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"), FakeTransport.json(200, "recovered"));
        assertEquals(
                "recovered",
                executor(t, APP, null, FAST_RETRY)
                        .executeAsync(APP_READ, RequestExecutorTest::text)
                        .get());
        assertEquals(2, t.requests().size());

        FakeTransport w =
                FakeTransport.returning(
                        FakeTransport.json(503, "{}"), FakeTransport.json(200, "{}"));
        var f =
                executor(w, APP, null, FAST_RETRY)
                        .executeAsync(APP_WRITE, RequestExecutorTest::text);
        assertInstanceOf(
                ServerException.class, assertThrows(ExecutionException.class, f::get).getCause());
        assertEquals(1, w.requests().size());
    }

    @Test
    void asyncMissingCredentialsFailTheFuture() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{}"));
        var f =
                executor(t, null, null, RetryPolicy.disabled())
                        .executeAsync(APP_READ, RequestExecutorTest::text);
        assertInstanceOf(
                IllegalStateException.class,
                assertThrows(ExecutionException.class, f::get).getCause());
    }

    @Test
    void retryPolicyValidatesArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryPolicy.exponentialBackoff(1, Duration.ZERO, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RetryPolicy.exponentialBackoff(
                                3, Duration.ofSeconds(5), Duration.ofSeconds(1)));
        assertFalse(RetryPolicy.disabled().enabled());
        assertEquals("RetryPolicy[disabled]", RetryPolicy.disabled().toString());
        assertNull(FAST_RETRY.delayBeforeRetry(3, null));
    }
}
