package io.github.alejo2075.qvapay.model;

/**
 * Response of {@code POST /v2/charge}.
 *
 * @param success whether the charge was applied
 * @param message message sent by the API
 * @param transaction the transaction created by the charge
 */
public record ChargeResponse(boolean success, String message, AppTransaction transaction) {}
