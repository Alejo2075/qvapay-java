package io.github.alejo2075.qvapay.webhook;

import io.github.alejo2075.qvapay.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload of the top-up webhook, sent when a crypto deposit is credited. It identifies the
 * transaction with {@code uuid} (not {@code transaction_uuid}) and is a different event from {@link
 * InvoiceWebhookEvent}.
 *
 * @param uuid UUID of the top-up transaction
 * @param status always {@code paid} in the documented version
 * @param isPartial {@code true} if less crypto than requested was received
 * @param amount USD actually credited, after fees (QvaPay sends a decimal string)
 * @param requestedUsd USD originally requested
 * @param receivedCrypto crypto amount received (sent as a string to keep precision)
 * @param coin coin tick, e.g. {@code BTC}
 * @param txId blockchain transaction hash, or {@code null}
 * @param paidAt credit time
 */
public record TopUpWebhookEvent(
        String uuid,
        TransactionStatus status,
        boolean isPartial,
        BigDecimal amount,
        BigDecimal requestedUsd,
        BigDecimal receivedCrypto,
        String coin,
        String txId,
        Instant paidAt) {}
