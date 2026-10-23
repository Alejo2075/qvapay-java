package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.alejo2075.qvapay.auth.AppCredentials;
import io.github.alejo2075.qvapay.auth.BearerTokenProvider;
import io.github.alejo2075.qvapay.exception.QvaPayException;
import io.github.alejo2075.qvapay.model.ChargeRequest;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.TestServer;
import io.github.alejo2075.qvapay.testsupport.TestServer.Stub;
import io.github.alejo2075.qvapay.webhook.WebhookVerificationException;
import io.github.alejo2075.qvapay.webhook.WebhookVerifier;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Distinctive secrets must not show up in any message, toString, stack trace or console output. */
class SecretsNeverLeakTest {

    private static final String SECRET = "SECRET-7f3a9c1d-do-not-leak";
    private static final String TOKEN = "TOKEN-b81e44aa-do-not-leak";
    private static final String APP_ID = "11111111-2222-3333-4444-555555555555";

    private static String fullText(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c).append('\n').append(c.getMessage()).append('\n');
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sb.append(sw).toString();
    }

    private static void assertClean(String text, String where) {
        assertFalse(text.contains(SECRET), "secret leaked in " + where);
        assertFalse(text.contains(TOKEN), "token leaked in " + where);
    }

    @Test
    void toStringOfEveryConfigurationObjectIsRedacted() {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{\"balance\":1}"));
        QvaPayClient client =
                QvaPayClient.builder()
                        .appCredentials(APP_ID, SECRET)
                        .bearerTokenProvider(BearerTokenProvider.of(TOKEN))
                        .transport(t)
                        .build();
        assertClean(client.toString(), "client");
        assertClean(AppCredentials.of(APP_ID, SECRET).toString(), "credentials");
        assertClean(BearerTokenProvider.of(TOKEN).toString(), "bearer provider");
        assertClean(WebhookVerifier.forSecret(SECRET).toString(), "webhook verifier");

        client.merchant().balanceAsync().join();
        assertClean(t.lastRequest().toString(), "ApiRequest");
        assertFalse(t.lastRequest().toString().contains("app-secret=" + SECRET));
    }

    @Test
    void requestObjectsDoNotPrintMoreThanNecessary() {
        assertClean(
                CreateInvoiceRequest.builder()
                        .amount(BigDecimal.ONE)
                        .description("d")
                        .remoteId("r")
                        .build()
                        .toString(),
                "invoice request");
        assertEquals(
                false, new ChargeRequest(BigDecimal.ONE, "user", "d", "r").toString().isEmpty());
    }

    @Test
    void exceptionsForEveryFailureKindNeverContainCredentials() {
        List<Object> outcomes =
                new ArrayList<>(
                        List.of(
                                FakeTransport.json(400, "{\"error\":\"bad\"}"),
                                FakeTransport.json(401, "{\"error\":\"Invalid credentials\"}"),
                                FakeTransport.json(
                                        403, "{\"code\":\"KYC_REQUIRED\",\"error\":\"kyc\"}"),
                                FakeTransport.json(404, "{}"),
                                FakeTransport.json(429, "{}"),
                                FakeTransport.json(500, "<html>boom</html>"),
                                FakeTransport.json(200, "not json"),
                                FakeTransport.networkError()));
        for (Object outcome : outcomes) {
            QvaPayClient client =
                    QvaPayClient.builder()
                            .appCredentials(APP_ID, SECRET)
                            .transport(FakeTransport.returning(outcome))
                            .build();
            QvaPayException e =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            QvaPayException.class, () -> client.merchant().info());
            assertClean(fullText(e), "exception for " + outcome);
        }
    }

    @Test
    void realTransportFailuresDoNotContainCredentialsEither() {
        {
            String dead;
            try (TestServer s = TestServer.start(Stub.json(200, "{}"))) {
                dead = s.baseUri().toString();
            }
            QvaPayClient client =
                    QvaPayClient.builder()
                            .baseUrl(dead)
                            .allowInsecureBaseUrlForTesting()
                            .appCredentials(APP_ID, SECRET)
                            .build();
            QvaPayException e =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            QvaPayException.class, () -> client.merchant().info());
            assertClean(fullText(e), "connection refused");
        }
        try (TestServer s = TestServer.start(Stub.json(200, "{}").delayed(1000))) {
            QvaPayClient client =
                    QvaPayClient.builder()
                            .baseUrl(s.baseUri().toString())
                            .allowInsecureBaseUrlForTesting()
                            .appCredentials(APP_ID, SECRET)
                            .requestTimeout(Duration.ofMillis(150))
                            .build();
            QvaPayException e =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            QvaPayException.class, () -> client.merchant().balance());
            assertClean(fullText(e), "timeout");
        }
    }

    @Test
    void webhookFailuresDoNotContainTheSecret() {
        WebhookVerificationException e =
                org.junit.jupiter.api.Assertions.assertThrows(
                        WebhookVerificationException.class,
                        () ->
                                WebhookVerifier.forSecret(SECRET)
                                        .verify("{}".getBytes(), "sha256=" + "a".repeat(64), "1"));
        assertClean(fullText(e), "webhook exception");
    }

    @Test
    void theSdkWritesNothingToStandardStreams() {
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream sink = new PrintStream(captured, true);
        System.setOut(sink);
        System.setErr(sink);
        try {
            QvaPayClient client =
                    QvaPayClient.builder()
                            .appCredentials(APP_ID, SECRET)
                            .transport(
                                    FakeTransport.returning(
                                            FakeTransport.json(500, "{}"),
                                            FakeTransport.json(200, "{\"balance\":1}")))
                            .build();
            try {
                client.merchant().balance();
            } catch (QvaPayException ignored) {
                // expected
            }
            client.merchant().balance();
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        assertEquals("", captured.toString());
    }
}
