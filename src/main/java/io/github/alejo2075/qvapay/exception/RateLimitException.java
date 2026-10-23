package io.github.alejo2075.qvapay.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP 429: the rate limit was exceeded. The request was not retried unless retries were enabled.
 */
public class RateLimitException extends QvaPayApiException {

    private static final long serialVersionUID = 1L;

    /** Delay requested by the server, may be null. */
    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param errorCode the stable programmatic code, may be {@code null}
     * @param apiMessage the API message, may be {@code null}
     * @param rateLimitHeaders non-sensitive rate limiting headers
     * @param retryAfter the delay requested by {@code Retry-After}, may be {@code null}
     */
    public RateLimitException(
            String errorCode,
            String apiMessage,
            Map<String, String> rateLimitHeaders,
            Duration retryAfter) {
        super(429, errorCode, apiMessage, rateLimitHeaders);
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the delay the server asked the client to wait.
     *
     * @return the {@code Retry-After} delay, if the response carried a usable one
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
