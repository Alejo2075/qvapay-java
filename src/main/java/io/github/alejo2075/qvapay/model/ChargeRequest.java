package io.github.alejo2075.qvapay.model;

import io.github.alejo2075.qvapay.internal.Validation;
import java.math.BigDecimal;

/**
 * Parameters of {@code POST /v2/charge}. <strong>This request moves money.</strong>
 *
 * @param amount amount in USD, greater than 0 and at most 100000 (never rounded)
 * @param userUuid UUID of the user to charge, who must have authorized your app
 * @param description description of the charge
 * @param remoteId your own reference; QvaPay documents no idempotency for it
 */
public record ChargeRequest(
        BigDecimal amount, String userUuid, String description, String remoteId) {

    /**
     * Validates the parameters.
     *
     * @param amount amount in USD
     * @param userUuid UUID of the user to charge
     * @param description description of the charge
     * @param remoteId your own reference
     * @throws IllegalArgumentException if a documented constraint is violated
     */
    public ChargeRequest {
        Validation.amount(amount, "amount");
        Validation.text(userUuid, "userUuid");
        Validation.text(description, "description");
        Validation.text(remoteId, "remoteId");
    }
}
