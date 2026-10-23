package io.github.alejo2075.qvapay.webhook;

/** Why a webhook was rejected. */
public enum WebhookFailure {
    /** The signature header is absent. Unsigned webhooks are always rejected. */
    MISSING_SIGNATURE,
    /** The signature header is not {@code sha256=<64 hex characters>}. */
    MALFORMED_SIGNATURE,
    /** The signature does not match the body (wrong secret, altered or re-serialized body). */
    SIGNATURE_MISMATCH,
    /** Timestamp checking is enabled and the header is absent. */
    MISSING_TIMESTAMP,
    /** The timestamp header is not a number of Unix seconds. */
    INVALID_TIMESTAMP,
    /** The timestamp is further from now than the configured tolerance. */
    TIMESTAMP_OUT_OF_RANGE,
    /** The signature is valid but the body is not the expected JSON payload. */
    INVALID_PAYLOAD
}
