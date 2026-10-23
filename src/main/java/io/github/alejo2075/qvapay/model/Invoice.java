package io.github.alejo2075.qvapay.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment invoice, returned when creating or modifying one.
 *
 * <p>Send the customer's browser to {@link #url()}; never build the payment URL yourself and never
 * send credentials to it. Keep {@link #transactionUuid()} next to your order.
 *
 * @param appId UUID of your app
 * @param amount invoice amount in USD
 * @param description payment description
 * @param remoteId your own identifier
 * @param transactionUuid UUID of the transaction in QvaPay
 * @param expireAt expiration time, or {@code null}
 * @param url payment URL to share with the customer
 */
public record Invoice(
        String appId,
        BigDecimal amount,
        String description,
        String remoteId,
        String transactionUuid,
        Instant expireAt,
        String url) {}
