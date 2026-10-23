package io.github.alejo2075.qvapay.webhook;

import io.github.alejo2075.qvapay.exception.QvaPayException;

/**
 * A webhook was rejected. Respond with a {@code 4xx} (for example 401) and do not process it. The
 * message never contains the body, the signature or the secret.
 */
public class WebhookVerificationException extends QvaPayException {

    private static final long serialVersionUID = 1L;

    /** Why the webhook was rejected. */
    private final WebhookFailure reason;

    /**
     * Creates the exception.
     *
     * @param reason why the webhook was rejected
     * @param cause the underlying cause, may be {@code null}
     */
    public WebhookVerificationException(WebhookFailure reason, Throwable cause) {
        super("Webhook rejected: " + reason, cause);
        this.reason = reason;
    }

    /**
     * Returns why the webhook was rejected.
     *
     * @return the failure reason
     */
    public WebhookFailure reason() {
        return reason;
    }
}
