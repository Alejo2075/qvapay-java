package io.github.alejo2075.qvapay.auth;

/** Which credentials an operation sends. Credentials of the other kind are never attached. */
public enum AuthPolicy {
    /** No credentials, e.g. the public coins list. */
    PUBLIC,
    /** {@code app-id} and {@code app-secret} headers. */
    APP,
    /** {@code Authorization: Bearer} header. */
    BEARER,
    /**
     * Either method is accepted by the API. App credentials win when configured, as the QvaPay docs
     * give them priority; only one method is ever sent.
     */
    APP_OR_BEARER
}
