package io.github.alejo2075.qvapay.webhook;

import io.github.alejo2075.qvapay.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload of the invoice webhook, sent when a payment changes state. It identifies the payment with
 * {@code transaction_uuid}; top-ups use a different event ({@link TopUpWebhookEvent}).
 *
 * <p>The webhook can arrive more than once. Use {@link #transactionUuid()} as the idempotency key.
 *
 * @param transactionUuid UUID of the transaction (the invoice's {@code transactionUuid})
 * @param remoteId your own identifier given when the invoice was created
 * @param status the status; deliver goods only for {@link TransactionStatus#PAID} and after
 *     confirming it
 * @param amount the amount in USD (QvaPay sends it as a string; parsed without rounding)
 * @param signed the {@code signed} field sent by QvaPay (documented only as a number, usually 0)
 * @param createdAt creation time
 * @param updatedAt last update time
 */
public record InvoiceWebhookEvent(
        String transactionUuid,
        String remoteId,
        TransactionStatus status,
        BigDecimal amount,
        Integer signed,
        Instant createdAt,
        Instant updatedAt) {}
