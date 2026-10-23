package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.model.InvoiceProduct;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Argument validation and wire formatting shared by request types. */
public final class Validation {

    /** Maximum amount accepted by the merchant endpoints (documented). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000");

    private static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Validation() {}

    /**
     * Requires a non-blank string whose length is within bounds.
     *
     * @param value the value
     * @param name the field name used in the message
     * @param maxLength the maximum length
     * @return the value
     */
    public static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    /**
     * Requires a non-blank string (no length limit).
     *
     * @param value the value
     * @param name the field name
     * @return the value
     */
    public static String text(String value, String name) {
        return text(value, name, Integer.MAX_VALUE);
    }

    /**
     * Requires an amount greater than zero and not above {@link #MAX_AMOUNT}. The value is never
     * rounded.
     *
     * @param amount the amount
     * @param name the field name
     * @return the amount
     */
    public static BigDecimal amount(BigDecimal amount, String name) {
        if (amount == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    name + " must be at most " + MAX_AMOUNT.toPlainString());
        }
        return amount;
    }

    /**
     * Requires an absolute http(s) URL with a host.
     *
     * @param value the URL
     * @param name the field name
     * @return the URL
     */
    public static String httpUrl(String value, String name) {
        text(value, name);
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
                return value;
            }
        } catch (URISyntaxException ignored) {
            // reported below
        }
        throw new IllegalArgumentException(name + " must be a valid http(s) URL");
    }

    /**
     * Validates product lines: non-blank name, non-negative price, positive quantity when given.
     *
     * @param products the products
     */
    public static void products(List<InvoiceProduct> products) {
        for (InvoiceProduct p : products) {
            text(p.name(), "product name");
            if (p.price() == null || p.price().signum() < 0) {
                throw new IllegalArgumentException("product price must not be negative");
            }
            if (p.quantity() != null && p.quantity().signum() <= 0) {
                throw new IllegalArgumentException("product quantity must be greater than 0");
            }
        }
    }

    /**
     * Requires an instant in the future.
     *
     * @param value the instant
     * @param name the field name
     * @return the instant
     */
    public static Instant future(Instant value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (!value.isAfter(Instant.now())) {
            throw new IllegalArgumentException(name + " must be in the future");
        }
        return value;
    }

    /**
     * Formats an instant like JavaScript's {@code toISOString()}: {@code 2026-12-31T23:59:59.000Z}.
     *
     * @param instant the instant
     * @return the ISO 8601 text in UTC with milliseconds
     */
    public static String iso(Instant instant) {
        return ISO_MILLIS.format(instant);
    }
}
