package io.github.alejo2075.qvapay.webhook;

import static io.github.alejo2075.qvapay.webhook.WebhookVerifierTest.CLOCK;
import static io.github.alejo2075.qvapay.webhook.WebhooksTest.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceWebhookTest {

    private static final WebhookVerifier VERIFIER =
            WebhookVerifier.builder(SECRET).clock(CLOCK).build();
    private static final String TS = Long.toString(WebhookVerifierTest.NOW.getEpochSecond());

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void fixtureMatchesTheBodyUsedByTheSignatureVectors() {
        assertEquals(WebhooksTest.BODY, Fixtures.load("webhook/invoice-paid.json"));
    }

    @Test
    void verifiedInvoiceWebhookIsParsedIntoTypedEvent() {
        byte[] body = bytes(Fixtures.load("webhook/invoice-paid.json"));
        VerifiedWebhook<InvoiceWebhookEvent> v =
                VERIFIER.verifyInvoice(body, Webhooks.sign(body, SECRET), TS);

        InvoiceWebhookEvent e = v.event();
        assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", e.transactionUuid());
        assertEquals("order-12345", e.remoteId());
        assertEquals(TransactionStatus.PAID, e.status());
        assertEquals(new BigDecimal("5.00"), e.amount());
        assertEquals(0, e.signed());
        assertEquals(Instant.parse("2026-10-05T12:00:00Z"), e.createdAt());
        assertEquals(Instant.parse("2026-10-05T12:00:30Z"), e.updatedAt());
        assertEquals(WebhookVerifierTest.NOW, v.timestamp().orElseThrow());
    }

    @Test
    void payloadIsNeverParsedBeforeTheSignatureIsVerified() {
        byte[] malformedJson = bytes("{this is not json");
        // wrong signature: must fail on the signature, not on the JSON
        assertEquals(
                WebhookFailure.SIGNATURE_MISMATCH,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        VERIFIER.verifyInvoice(
                                                malformedJson, "sha256=" + "1".repeat(64), TS))
                        .reason());
        // right signature but broken JSON: fails as invalid payload
        assertEquals(
                WebhookFailure.INVALID_PAYLOAD,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        VERIFIER.verifyInvoice(
                                                malformedJson,
                                                Webhooks.sign(malformedJson, SECRET),
                                                TS))
                        .reason());
    }

    @Test
    void authenticPayloadWithoutTransactionUuidIsInvalid() {
        byte[] topUpShaped = bytes("{\"uuid\":\"abc\",\"status\":\"paid\"}");
        assertEquals(
                WebhookFailure.INVALID_PAYLOAD,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        VERIFIER.verifyInvoice(
                                                topUpShaped,
                                                Webhooks.sign(topUpShaped, SECRET),
                                                TS))
                        .reason());
    }

    @Test
    void unknownStatusesAndExtraFieldsDoNotBreakParsing() {
        byte[] body =
                bytes(
                        "{\"transaction_uuid\":\"u\",\"status\":\"refunded\",\"amount\":\"1.50\",\"extra\":[1,2]}");
        var e = VERIFIER.verifyInvoice(body, Webhooks.sign(body, SECRET), TS).event();
        assertEquals(TransactionStatus.UNKNOWN, e.status());
        assertEquals(new BigDecimal("1.50"), e.amount());
    }

    @Test
    void amountAcceptsNumbersToo() {
        byte[] body = bytes("{\"transaction_uuid\":\"u\",\"amount\":5.00}");
        assertEquals(
                new BigDecimal("5.00"),
                VERIFIER.verifyInvoice(body, Webhooks.sign(body, SECRET), TS).event().amount());
    }

    @Test
    void timestampIsEmptyWhenNotChecked() {
        byte[] body = bytes(Fixtures.load("webhook/invoice-paid.json"));
        var v =
                WebhookVerifier.builder(SECRET)
                        .ignoreTimestamp()
                        .build()
                        .verifyInvoice(body, Webhooks.sign(body, SECRET), null);
        assertTrue(v.timestamp().isEmpty());
    }
}
