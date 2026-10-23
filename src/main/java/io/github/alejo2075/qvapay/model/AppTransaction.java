package io.github.alejo2075.qvapay.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A transaction of the app: an invoice, or the transaction created by a direct charge.
 *
 * @param uuid transaction UUID
 * @param amount amount in USD
 * @param description payment description
 * @param products product lines, or {@code null} when the endpoint does not return them
 * @param remoteId your own identifier
 * @param status transaction status
 * @param createdAt creation time
 * @param updatedAt last update time
 * @param paidBy the payer ({@code PaidBy} on the wire, capital P), or {@code null} when the
 *     transaction is not paid or the endpoint does not return it
 */
public record AppTransaction(
        String uuid,
        BigDecimal amount,
        String description,
        List<InvoiceProduct> products,
        String remoteId,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt,
        @JsonProperty("PaidBy") PayerInfo paidBy) {}
