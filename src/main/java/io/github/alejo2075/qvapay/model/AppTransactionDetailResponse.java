package io.github.alejo2075.qvapay.model;

/**
 * Response of {@code POST /v2/transactions/{uuid}}.
 *
 * @param message message sent by the API (e.g. {@code "APP Transaction"})
 * @param transaction the transaction, with {@link AppTransaction#paidBy()} once paid
 */
public record AppTransactionDetailResponse(String message, AppTransaction transaction) {}
