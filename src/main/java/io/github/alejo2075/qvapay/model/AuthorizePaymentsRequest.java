package io.github.alejo2075.qvapay.model;

import io.github.alejo2075.qvapay.internal.Validation;

/**
 * Parameters of {@code POST /v2/authorize_payments}: asks QvaPay for a URL where a user can
 * authorize direct charges from your app.
 *
 * @param remoteId identifier of the user in your own system
 * @param callback URL where QvaPay redirects the user after the authorization (http or https)
 */
public record AuthorizePaymentsRequest(String remoteId, String callback) {

    /**
     * Validates the parameters.
     *
     * @param remoteId identifier of the user in your own system
     * @param callback URL where QvaPay redirects the user after the authorization
     * @throws IllegalArgumentException if a value is blank or the callback is not an http(s) URL
     */
    public AuthorizePaymentsRequest {
        Validation.text(remoteId, "remoteId");
        Validation.httpUrl(callback, "callback");
    }
}
