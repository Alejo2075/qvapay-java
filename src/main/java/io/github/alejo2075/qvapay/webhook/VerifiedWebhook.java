package io.github.alejo2075.qvapay.webhook;

import java.time.Instant;
import java.util.Optional;

/**
 * A webhook whose signature (and, if enabled, timestamp) was verified before the payload was
 * parsed.
 *
 * <p>Verified means "sent by someone who knows your app secret". It does not mean "new" or "true
 * for your order": deduplicate by the event's transaction UUID and compare amount, status and
 * ownership with your own records before acting.
 *
 * @param <T> the event type
 * @param event the parsed payload
 * @param sentAt the instant from {@code x-qvapay-timestamp}, or {@code null} if the header was
 *     absent or not checked
 */
public record VerifiedWebhook<T>(T event, Instant sentAt) {

    /**
     * Returns the delivery timestamp.
     *
     * @return the timestamp, if the header was present and valid
     */
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(sentAt);
    }
}
