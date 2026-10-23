package io.github.alejo2075.qvapay.model;

/**
 * Response of {@code POST /v2/authorize_payments}.
 *
 * <p>Redirect the user's browser to {@link #url()}. They sign in to QvaPay and grant (or deny)
 * permission themselves; the SDK never simulates that consent.
 *
 * @param message message sent by the API
 * @param url the authorization URL to open in the user's browser
 */
public record AuthorizePaymentsResponse(String message, String url) {}
