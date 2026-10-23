package io.github.alejo2075.qvapay.http;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Opt-in retry policy for <strong>read-only</strong> operations.
 *
 * <p>Retries are disabled by default. Even when enabled, the SDK only retries operations it knows
 * to be safe (reads such as {@code info}, {@code balance} and transaction queries, which are {@code
 * POST} requests on the wire). Operations that create resources or move money, such as {@code
 * create_invoice}, {@code modify_invoice}, {@code authorize_payments} and {@code charge}, are
 * <strong>never</strong> retried, because QvaPay documents no idempotency guarantee for them.
 *
 * <p>Retried failures are network errors, HTTP 429 and HTTP 5xx. Waits use exponential backoff with
 * jitter. A {@code Retry-After} header is honoured; if it asks for more than {@code maxDelay} the
 * failure is reported instead of retrying too early.
 */
public final class RetryPolicy {

    private static final RetryPolicy DISABLED = new RetryPolicy(1, Duration.ZERO, Duration.ZERO);

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;

    private RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    /**
     * Returns the policy that never retries (the default).
     *
     * @return the disabled policy
     */
    public static RetryPolicy disabled() {
        return DISABLED;
    }

    /**
     * Creates a policy with exponential backoff and jitter.
     *
     * @param maxAttempts total attempts including the first one (2 to 10)
     * @param baseDelay delay before the first retry (before jitter)
     * @param maxDelay upper bound for any single wait
     * @return the policy
     * @throws IllegalArgumentException if a value is out of range
     */
    public static RetryPolicy exponentialBackoff(
            int maxAttempts, Duration baseDelay, Duration maxDelay) {
        if (maxAttempts < 2 || maxAttempts > 10) {
            throw new IllegalArgumentException("maxAttempts must be between 2 and 10");
        }
        if (baseDelay.isNegative() || maxDelay.isNegative() || baseDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("delays must satisfy 0 <= baseDelay <= maxDelay");
        }
        return new RetryPolicy(maxAttempts, baseDelay, maxDelay);
    }

    /**
     * Returns whether retries are enabled.
     *
     * @return {@code true} if more than one attempt is allowed
     */
    public boolean enabled() {
        return maxAttempts > 1;
    }

    /**
     * Returns the maximum number of attempts, including the first.
     *
     * @return the attempts
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Computes how long to wait before the next attempt.
     *
     * @param failedAttempt the number of the attempt that just failed (1-based)
     * @param retryAfter the delay requested by the server, or {@code null}
     * @return the wait, or {@code null} if no further attempt should be made
     */
    public Duration delayBeforeRetry(int failedAttempt, Duration retryAfter) {
        if (failedAttempt >= maxAttempts) {
            return null;
        }
        if (retryAfter != null) {
            return retryAfter.compareTo(maxDelay) > 0 ? null : retryAfter;
        }
        long exp = baseDelay.toMillis() * (1L << Math.min(failedAttempt - 1, 20));
        long capped = Math.min(exp, maxDelay.toMillis());
        long jittered =
                capped / 2
                        + (capped > 1 ? ThreadLocalRandom.current().nextLong(capped / 2 + 1) : 0);
        return Duration.ofMillis(jittered);
    }

    @Override
    public String toString() {
        return enabled()
                ? "RetryPolicy[maxAttempts="
                        + maxAttempts
                        + ", baseDelay="
                        + baseDelay
                        + ", maxDelay="
                        + maxDelay
                        + "]"
                : "RetryPolicy[disabled]";
    }
}
