package io.github.alejo2075.qvapay.model;

import java.math.BigDecimal;

/**
 * Balance of the app owner, returned by {@code POST /v2/balance}. The API may answer with {@code
 * {"balance": 150.75}} (documented) or with the bare number (seen in test mode); both are accepted.
 *
 * @param balance current balance in USD
 */
public record AppBalance(BigDecimal balance) {}
