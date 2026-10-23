package io.github.alejo2075.qvapay.webhook;

import static io.github.alejo2075.qvapay.webhook.WebhookVerifierTest.CLOCK;
import static io.github.alejo2075.qvapay.webhook.WebhooksTest.SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TopUpWebhookTest {

    private static final WebhookVerifier VERIFIER =
            WebhookVerifier.builder(SECRET).clock(CLOCK).build();
    private static final String TS = Long.toString(WebhookVerifierTest.NOW.getEpochSecond());

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void verifiedTopUpIsParsedUsingUuidAndStringAmounts() {
        byte[] body = bytes(Fixtures.load("webhook/topup-paid.json"));
        TopUpWebhookEvent e = VERIFIER.verifyTopUp(body, Webhooks.sign(body, SECRET), TS).event();

        assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", e.uuid());
        assertEquals(TransactionStatus.PAID, e.status());
        assertFalse(e.isPartial());
        assertEquals(new BigDecimal("100.00"), e.amount());
        assertEquals(0, new BigDecimal("100").compareTo(e.requestedUsd()));
        assertEquals(new BigDecimal("0.00232558"), e.receivedCrypto());
        assertEquals("BTC", e.coin());
        assertEquals("0xabc123...blockchain_hash", e.txId());
        assertEquals(Instant.parse("2026-04-30T14:32:18.421Z"), e.paidAt());
    }

    @Test
    void partialPaymentsAndNullTxIdAreRepresented() {
        byte[] body =
                bytes(
                        "{\"uuid\":\"u\",\"status\":\"paid\",\"is_partial\":true,\"amount\":\"40.00\",\"requested_usd\":100,\"received_crypto\":\"0.001\",\"coin\":\"BTC\",\"tx_id\":null}");
        TopUpWebhookEvent e = VERIFIER.verifyTopUp(body, Webhooks.sign(body, SECRET), TS).event();
        assertTrue(e.isPartial());
        assertNull(e.txId());
        assertEquals(new BigDecimal("40.00"), e.amount());
    }

    @Test
    void unsignedTopUpWebhooksAreRejected() {
        byte[] body = bytes(Fixtures.load("webhook/topup-paid.json"));
        // A top-up created with a user Bearer token arrives without x-qvapay-signature.
        assertEquals(
                WebhookFailure.MISSING_SIGNATURE,
                assertThrows(
                                WebhookVerificationException.class,
                                () -> VERIFIER.verifyTopUp(body, null, TS))
                        .reason());
        // Not even when timestamp checking is disabled.
        WebhookVerifier lenient = WebhookVerifier.builder(SECRET).ignoreTimestamp().build();
        assertEquals(
                WebhookFailure.MISSING_SIGNATURE,
                assertThrows(
                                WebhookVerificationException.class,
                                () -> lenient.verifyTopUp(body, "", null))
                        .reason());
    }

    @Test
    void invoiceAndTopUpEventsAreNotInterchangeable() {
        byte[] invoice = bytes(Fixtures.load("webhook/invoice-paid.json"));
        byte[] topUp = bytes(Fixtures.load("webhook/topup-paid.json"));
        assertEquals(
                WebhookFailure.INVALID_PAYLOAD,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        VERIFIER.verifyTopUp(
                                                invoice, Webhooks.sign(invoice, SECRET), TS))
                        .reason());
        assertEquals(
                WebhookFailure.INVALID_PAYLOAD,
                assertThrows(
                                WebhookVerificationException.class,
                                () ->
                                        VERIFIER.verifyInvoice(
                                                topUp, Webhooks.sign(topUp, SECRET), TS))
                        .reason());
    }

    @Test
    void tamperedTopUpAmountIsRejected() {
        byte[] body = bytes(Fixtures.load("webhook/topup-paid.json"));
        String sig = Webhooks.sign(body, SECRET);
        byte[] tampered =
                bytes(
                        Fixtures.load("webhook/topup-paid.json")
                                .replace("\"100.00\"", "\"9999.00\""));
        assertEquals(
                WebhookFailure.SIGNATURE_MISMATCH,
                assertThrows(
                                WebhookVerificationException.class,
                                () -> VERIFIER.verifyTopUp(tampered, sig, TS))
                        .reason());
    }
}
