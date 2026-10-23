package io.github.alejo2075.qvapay.webhook;

import static io.github.alejo2075.qvapay.webhook.WebhooksTest.BODY;
import static io.github.alejo2075.qvapay.webhook.WebhooksTest.BODY_SIGNATURE;
import static io.github.alejo2075.qvapay.webhook.WebhooksTest.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WebhookVerifierTest {

    static final Instant NOW = Instant.parse("2026-10-05T12:00:30Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static byte[] body() {
        return BODY.getBytes(StandardCharsets.UTF_8);
    }

    private static String ts(long offsetSeconds) {
        return Long.toString(NOW.getEpochSecond() + offsetSeconds);
    }

    private static WebhookVerifier verifier() {
        return WebhookVerifier.builder(SECRET).clock(CLOCK).build();
    }

    private static WebhookFailure failure(String sig, String timestamp) {
        return assertThrows(
                        WebhookVerificationException.class,
                        () -> verifier().verify(body(), sig, timestamp))
                .reason();
    }

    @Test
    void validSignatureAndFreshTimestampPass() {
        verifier().verify(body(), BODY_SIGNATURE, ts(0));
        assertTrue(verifier().isValid(body(), BODY_SIGNATURE, ts(-5)));
    }

    @Test
    void toleranceIsInclusiveAndSymmetric() {
        assertTrue(verifier().isValid(body(), BODY_SIGNATURE, ts(-300)));
        assertTrue(verifier().isValid(body(), BODY_SIGNATURE, ts(300)));
        assertFalse(verifier().isValid(body(), BODY_SIGNATURE, ts(-301)));
        assertFalse(verifier().isValid(body(), BODY_SIGNATURE, ts(301)));
        assertEquals(WebhookFailure.TIMESTAMP_OUT_OF_RANGE, failure(BODY_SIGNATURE, ts(-3600)));
        assertEquals(WebhookFailure.TIMESTAMP_OUT_OF_RANGE, failure(BODY_SIGNATURE, ts(3600)));
    }

    @Test
    void customToleranceApplies() {
        WebhookVerifier strict =
                WebhookVerifier.builder(SECRET)
                        .clock(CLOCK)
                        .timestampTolerance(Duration.ofSeconds(10))
                        .build();
        assertTrue(strict.isValid(body(), BODY_SIGNATURE, ts(-10)));
        assertFalse(strict.isValid(body(), BODY_SIGNATURE, ts(-11)));
    }

    @Test
    void missingOrInvalidTimestampIsRejectedWhenChecking() {
        assertEquals(WebhookFailure.MISSING_TIMESTAMP, failure(BODY_SIGNATURE, null));
        assertEquals(WebhookFailure.MISSING_TIMESTAMP, failure(BODY_SIGNATURE, "  "));
        assertEquals(WebhookFailure.INVALID_TIMESTAMP, failure(BODY_SIGNATURE, "yesterday"));
        assertEquals(WebhookFailure.INVALID_TIMESTAMP, failure(BODY_SIGNATURE, "12.5"));
        assertEquals(
                WebhookFailure.INVALID_TIMESTAMP, failure(BODY_SIGNATURE, "99999999999999999999"));
    }

    @Test
    void ignoreTimestampOnlyChecksTheSignature() {
        WebhookVerifier v = WebhookVerifier.builder(SECRET).ignoreTimestamp().build();
        assertTrue(v.isValid(body(), BODY_SIGNATURE, null));
        assertTrue(v.isValid(body(), BODY_SIGNATURE, "garbage"));
        assertFalse(v.isValid(body(), "sha256=" + "0".repeat(64), null));
    }

    @Test
    void signatureProblemsAreReportedBeforeTimestampProblems() {
        assertEquals(WebhookFailure.MISSING_SIGNATURE, failure(null, null));
        assertEquals(WebhookFailure.MISSING_SIGNATURE, failure("", ts(0)));
        assertEquals(WebhookFailure.MALFORMED_SIGNATURE, failure("sha256=abc", ts(0)));
        assertEquals(
                WebhookFailure.MALFORMED_SIGNATURE,
                failure("sha1=" + BODY_SIGNATURE.substring(7), ts(0)));
        assertEquals(WebhookFailure.SIGNATURE_MISMATCH, failure("sha256=" + "0".repeat(64), ts(0)));
    }

    @Test
    void tamperedAndReserializedBodiesAreMismatches() {
        assertEquals(
                WebhookFailure.SIGNATURE_MISMATCH,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        verifier()
                                                .verify(
                                                        BODY.replace("paid", "pending")
                                                                .getBytes(StandardCharsets.UTF_8),
                                                        BODY_SIGNATURE,
                                                        ts(0)))
                        .reason());
        assertEquals(
                WebhookFailure.SIGNATURE_MISMATCH,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        verifier()
                                                .verify(
                                                        BODY.replace(",", ", ")
                                                                .getBytes(StandardCharsets.UTF_8),
                                                        BODY_SIGNATURE,
                                                        ts(0)))
                        .reason());
    }

    @Test
    void exceptionAndVerifierNeverExposeSecretSignatureOrBody() {
        WebhookVerificationException e =
                assertThrows(
                        WebhookVerificationException.class,
                        () -> verifier().verify(body(), "sha256=" + "0".repeat(64), ts(0)));
        assertFalse(e.getMessage().contains(SECRET));
        assertFalse(e.getMessage().contains("0000"));
        assertFalse(e.getMessage().contains("order-12345"));
        assertFalse(verifier().toString().contains(SECRET));
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WebhookVerifier.forSecret(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> WebhookVerifier.builder(SECRET).timestampTolerance(Duration.ZERO));
    }
}
