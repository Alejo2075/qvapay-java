package io.github.alejo2075.qvapay.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/**
 * Status of an app transaction. QvaPay documents {@code pending}, {@code paid} and {@code
 * cancelled}; any other value maps to {@link #UNKNOWN} instead of failing, so new statuses do not
 * break existing integrations. Treat {@code UNKNOWN} as "not paid".
 */
public enum TransactionStatus {
    /** Waiting for the customer. */
    PENDING,
    /** Paid. */
    PAID,
    /** Cancelled. */
    CANCELLED,
    /** A value this SDK version does not know. */
    UNKNOWN;

    /**
     * Parses the wire value, ignoring case.
     *
     * @param value the status text sent by the API
     * @return the matching status, or {@link #UNKNOWN}
     */
    @JsonCreator
    public static TransactionStatus fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "pending":
                return PENDING;
            case "paid":
                return PAID;
            case "cancelled":
                return CANCELLED;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Returns the lower-case value used on the wire.
     *
     * @return the wire value ({@code "unknown"} for {@link #UNKNOWN})
     */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
