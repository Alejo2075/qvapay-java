package io.github.alejo2075.qvapay.model;

import java.math.BigDecimal;

/**
 * Balance of the app owner, returned by {@code POST /v2/balance}.
 *
 * @param balance current balance in USD
 */
public record AppBalance(BigDecimal balance) {}
