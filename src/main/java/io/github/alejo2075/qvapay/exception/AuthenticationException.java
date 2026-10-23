package io.github.alejo2075.qvapay.exception;

import java.util.Map;

/** HTTP 401: invalid credentials or expired token. */
public class AuthenticationException extends QvaPayApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param statusCode the HTTP status code
     * @param errorCode the stable programmatic code, may be {@code null}
     * @param apiMessage the API message, may be {@code null}
     * @param rateLimitHeaders non-sensitive rate limiting headers
     */
    public AuthenticationException(
            int statusCode,
            String errorCode,
            String apiMessage,
            Map<String, String> rateLimitHeaders) {
        super(statusCode, errorCode, apiMessage, rateLimitHeaders);
    }
}
