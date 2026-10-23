package io.github.alejo2075.qvapay.model;

import java.util.List;

/**
 * Response of {@code POST /v2/transactions}: the transactions of the requested page, newest first.
 * QvaPay documents no pagination metadata, so none is modelled: a page shorter than {@code take}
 * (or empty) means there is nothing more.
 *
 * @param message message sent by the API (e.g. {@code "APP Transactions"})
 * @param transactions the transactions of this page, empty if none
 */
public record AppTransactionPageResponse(String message, List<AppTransaction> transactions) {

    /**
     * Normalizes a missing list to an empty one.
     *
     * @param message message sent by the API
     * @param transactions the transactions, may be {@code null}
     */
    public AppTransactionPageResponse {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}
