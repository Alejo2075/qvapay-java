package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.model.AuthorizePaymentsRequest;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PaymentsApiAuthorizeTest {

    private static final AuthorizePaymentsRequest REQ =
            new AuthorizePaymentsRequest("user-001", "https://mitienda.com/auth/callback");

    @Test
    void authorizePostsRemoteIdAndCallbackWithAppHeaders() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/authorize-payments.json")));
        var r = Clients.withApp(t).payments().authorize(REQ);

        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/authorize_payments", req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        assertEquals(
                "{\"remote_id\":\"user-001\",\"callback\":\"https://mitienda.com/auth/callback\"}",
                new String(req.body(), StandardCharsets.UTF_8));

        assertEquals("Authorize payments", r.message());
        assertEquals(true, r.url().startsWith("https://qvapay.com/authorize_payments?app_uuid="));
    }

    @Test
    void missingPermissionIsAValidationException() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(
                                400,
                                "{\"error\":\"App has no payment authorization permission\"}"));
        assertThrows(ValidationException.class, () -> Clients.withApp(t).payments().authorize(REQ));
    }

    @Test
    void localValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizePaymentsRequest("", "https://x.test/cb"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizePaymentsRequest("u", "not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizePaymentsRequest("u", null));
    }

    @Test
    void asyncFormAndNoRetry() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/authorize-payments.json")));
        assertEquals(
                "Authorize payments",
                Clients.withApp(t).payments().authorizeAsync(REQ).get().message());

        FakeTransport failing =
                FakeTransport.returning(
                        FakeTransport.json(500, "{}"), FakeTransport.json(200, "{}"));
        var client =
                QvaPayClient.builder()
                        .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                        .transport(failing)
                        .retryPolicy(
                                io.github.alejo2075.qvapay.http.RetryPolicy.exponentialBackoff(
                                        3,
                                        java.time.Duration.ofMillis(1),
                                        java.time.Duration.ofMillis(2)))
                        .build();
        assertThrows(
                io.github.alejo2075.qvapay.exception.ServerException.class,
                () -> client.payments().authorize(REQ));
        assertEquals(1, failing.requests().size());
    }
}
