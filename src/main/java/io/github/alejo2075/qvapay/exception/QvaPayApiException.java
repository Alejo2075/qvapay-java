package io.github.alejo2075.qvapay.exception;

import java.util.Map;

/**
 * The QvaPay API answered with a non-successful HTTP status.
 *
 * <p>Application logic should branch on {@link #errorCode()} (the stable {@code code} field of the
 * error body) when it is present, never on the text of {@link #getMessage()}, which may change.
 */
public class QvaPayApiException extends QvaPayException {

    private static final long serialVersionUID = 1L;

    /** HTTP status code. */
    private final int statusCode;

    /** Stable programmatic code, may be null. */
    private final String errorCode;

    /** Message sent by the API, may be null. */
    private final String apiMessage;

    /** Non-sensitive rate limiting headers. */
    private final Map<String, String> rateLimitHeaders;

    /**
     * Creates an API exception.
     *
     * @param statusCode the HTTP status code
     * @param errorCode the stable programmatic {@code code}, or {@code null} if the body had none
     * @param apiMessage the {@code error}/{@code message} text sent by the API, may be {@code null}
     * @param rateLimitHeaders non-sensitive rate limiting headers (lower-case names), may be empty
     */
    public QvaPayApiException(
            int statusCode,
            String errorCode,
            String apiMessage,
            Map<String, String> rateLimitHeaders) {
        super(describe(statusCode, errorCode, apiMessage));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.apiMessage = apiMessage;
        this.rateLimitHeaders = rateLimitHeaders == null ? Map.of() : Map.copyOf(rateLimitHeaders);
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the stable programmatic error code such as {@code KYC_REQUIRED}.
     *
     * @return the code, or {@code null} when the API did not send one
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * Returns the human readable message sent by the API.
     *
     * @return the API message, or {@code null}
     */
    public String apiMessage() {
        return apiMessage;
    }

    /**
     * Returns rate limiting headers received with the response (such as {@code retry-after}).
     *
     * @return an unmodifiable map with lower-case header names
     */
    public Map<String, String> rateLimitHeaders() {
        return rateLimitHeaders;
    }

    private static String describe(int statusCode, String errorCode, String apiMessage) {
        StringBuilder sb = new StringBuilder("QvaPay API error (HTTP ").append(statusCode);
        if (errorCode != null) {
            sb.append(", code=").append(errorCode);
        }
        sb.append(')');
        if (apiMessage != null && !apiMessage.isBlank()) {
            sb.append(": ").append(apiMessage);
        }
        return sb.toString();
    }
}
