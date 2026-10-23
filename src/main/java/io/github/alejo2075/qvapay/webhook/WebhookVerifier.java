package io.github.alejo2075.qvapay.webhook;

import io.github.alejo2075.qvapay.exception.SerializationException;
import io.github.alejo2075.qvapay.internal.JsonCodec;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Verifies QvaPay webhooks: signature on the raw body and, by default, freshness of the timestamp.
 *
 * <p>Instances are immutable and thread-safe. Typical use in any framework:
 *
 * <pre>{@code
 * WebhookVerifier verifier = WebhookVerifier.forSecret(System.getenv("QVAPAY_APP_SECRET"));
 * // rawBody: the exact request bytes, read BEFORE any JSON parsing
 * verifier.verify(rawBody, request.header("x-qvapay-signature"), request.header("x-qvapay-timestamp"));
 * }</pre>
 *
 * <p><strong>The timestamp is not a replay defence.</strong> It is not part of the signed material,
 * so an attacker who captured a genuine message can attach a fresh timestamp. The tolerance only
 * discards stale deliveries. You must also make processing idempotent (use {@code
 * transaction_uuid}) and confirm the amount, status and ownership against your own order, or
 * against {@code transactions().get(uuid)}, before delivering goods.
 */
public final class WebhookVerifier {

    /** Default accepted distance between the timestamp and now (the official plugin uses 300 s). */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);

    private final String secret;
    private final boolean checkTimestamp;
    private final Duration tolerance;
    private final Clock clock;

    private WebhookVerifier(Builder b) {
        this.secret = b.secret;
        this.checkTimestamp = b.checkTimestamp;
        this.tolerance = b.tolerance;
        this.clock = b.clock;
    }

    /**
     * Creates a verifier with the default settings: timestamp required, ±300 seconds, system clock.
     *
     * @param appSecret your application secret
     * @return the verifier
     */
    public static WebhookVerifier forSecret(String appSecret) {
        return builder(appSecret).build();
    }

    /**
     * Starts a builder.
     *
     * @param appSecret your application secret
     * @return a new builder
     */
    public static Builder builder(String appSecret) {
        return new Builder(appSecret);
    }

    /**
     * Verifies a webhook and throws if it must be rejected.
     *
     * @param rawBody the exact bytes of the request body
     * @param signatureHeader value of {@code x-qvapay-signature}, may be {@code null}
     * @param timestampHeader value of {@code x-qvapay-timestamp}, may be {@code null}
     * @throws WebhookVerificationException if the signature or the timestamp is not acceptable
     */
    public void verify(byte[] rawBody, String signatureHeader, String timestampHeader) {
        Webhooks.requireInputs(rawBody, secret);
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookVerificationException(WebhookFailure.MISSING_SIGNATURE, null);
        }
        byte[] provided = Webhooks.decodeSignature(signatureHeader);
        if (provided == null) {
            throw new WebhookVerificationException(WebhookFailure.MALFORMED_SIGNATURE, null);
        }
        if (!MessageDigest.isEqual(Webhooks.hmac(rawBody, secret), provided)) {
            throw new WebhookVerificationException(WebhookFailure.SIGNATURE_MISMATCH, null);
        }
        if (checkTimestamp) {
            checkTimestamp(timestampHeader);
        }
    }

    /**
     * Non-throwing form of {@link #verify(byte[], String, String)}.
     *
     * @param rawBody the exact bytes of the request body
     * @param signatureHeader value of {@code x-qvapay-signature}
     * @param timestampHeader value of {@code x-qvapay-timestamp}
     * @return {@code true} if the webhook is acceptable
     */
    public boolean isValid(byte[] rawBody, String signatureHeader, String timestampHeader) {
        try {
            verify(rawBody, signatureHeader, timestampHeader);
            return true;
        } catch (WebhookVerificationException e) {
            return false;
        }
    }

    /**
     * Verifies an invoice-payment webhook and, only if it is authentic, parses the payload.
     *
     * @param rawBody the exact bytes of the request body
     * @param signatureHeader value of {@code x-qvapay-signature}
     * @param timestampHeader value of {@code x-qvapay-timestamp}
     * @return the verified event
     * @throws WebhookVerificationException if it must be rejected, including a valid signature over
     *     a payload without {@code transaction_uuid}
     */
    public VerifiedWebhook<InvoiceWebhookEvent> verifyInvoice(
            byte[] rawBody, String signatureHeader, String timestampHeader) {
        verify(rawBody, signatureHeader, timestampHeader);
        InvoiceWebhookEvent event = parse(rawBody, InvoiceWebhookEvent.class);
        if (event.transactionUuid() == null || event.transactionUuid().isBlank()) {
            throw new WebhookVerificationException(WebhookFailure.INVALID_PAYLOAD, null);
        }
        return new VerifiedWebhook<>(event, parseTimestampOrNull(timestampHeader));
    }

    /**
     * Verifies a top-up webhook and, only if it is authentic, parses the payload.
     *
     * <p>Top-up webhooks are signed only when the top-up was created with app credentials. A
     * webhook without signature is <strong>always rejected</strong> ({@link
     * WebhookFailure#MISSING_SIGNATURE}): create top-ups with app credentials, not with a user
     * Bearer token, if you depend on this notification.
     *
     * @param rawBody the exact bytes of the request body
     * @param signatureHeader value of {@code x-qvapay-signature}
     * @param timestampHeader value of {@code x-qvapay-timestamp}
     * @return the verified event
     * @throws WebhookVerificationException if it must be rejected, including a valid signature over
     *     a payload without {@code uuid} (for example an invoice webhook)
     */
    public VerifiedWebhook<TopUpWebhookEvent> verifyTopUp(
            byte[] rawBody, String signatureHeader, String timestampHeader) {
        verify(rawBody, signatureHeader, timestampHeader);
        TopUpWebhookEvent event = parse(rawBody, TopUpWebhookEvent.class);
        if (event.uuid() == null || event.uuid().isBlank()) {
            throw new WebhookVerificationException(WebhookFailure.INVALID_PAYLOAD, null);
        }
        return new VerifiedWebhook<>(event, parseTimestampOrNull(timestampHeader));
    }

    private static <T> T parse(byte[] rawBody, Class<T> type) {
        try {
            return JsonCodec.read(rawBody, type);
        } catch (SerializationException e) {
            throw new WebhookVerificationException(WebhookFailure.INVALID_PAYLOAD, e);
        }
    }

    Instant parseTimestampOrNull(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestampHeader.trim()));
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    private void checkTimestamp(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new WebhookVerificationException(WebhookFailure.MISSING_TIMESTAMP, null);
        }
        Instant sent = parseTimestampOrNull(timestampHeader);
        if (sent == null) {
            throw new WebhookVerificationException(WebhookFailure.INVALID_TIMESTAMP, null);
        }
        Duration distance = Duration.between(sent, clock.instant()).abs();
        if (distance.compareTo(tolerance) > 0) {
            throw new WebhookVerificationException(WebhookFailure.TIMESTAMP_OUT_OF_RANGE, null);
        }
    }

    @Override
    public String toString() {
        return "WebhookVerifier[checkTimestamp="
                + checkTimestamp
                + ", tolerance="
                + tolerance
                + "]";
    }

    /** Builder for {@link WebhookVerifier}. */
    public static final class Builder {

        private final String secret;
        private boolean checkTimestamp = true;
        private Duration tolerance = DEFAULT_TOLERANCE;
        private Clock clock = Clock.systemUTC();

        private Builder(String secret) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("appSecret is required");
            }
            this.secret = secret;
        }

        /**
         * Sets how far the timestamp may be from now, in either direction (default 300 s).
         *
         * @param tolerance a positive duration
         * @return this builder
         */
        public Builder timestampTolerance(Duration tolerance) {
            Objects.requireNonNull(tolerance, "tolerance");
            if (tolerance.isZero() || tolerance.isNegative()) {
                throw new IllegalArgumentException("tolerance must be positive");
            }
            this.tolerance = tolerance;
            this.checkTimestamp = true;
            return this;
        }

        /**
         * Stops checking the timestamp header; only the signature is verified. Use it if your proxy
         * drops the header. You lose the staleness check, so rely on deduplication.
         *
         * @return this builder
         */
        public Builder ignoreTimestamp() {
            this.checkTimestamp = false;
            return this;
        }

        /**
         * Sets the clock used to read "now" (inject a fixed clock in tests).
         *
         * @param clock the clock
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * Builds the verifier.
         *
         * @return the verifier
         */
        public WebhookVerifier build() {
            return new WebhookVerifier(this);
        }
    }
}
