package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.auth.AppCredentials;
import io.github.alejo2075.qvapay.auth.BearerTokenProvider;
import io.github.alejo2075.qvapay.http.HttpTransport;
import io.github.alejo2075.qvapay.http.RetryPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * Immutable settings of a configured client.
 *
 * @param transport the HTTP transport
 * @param baseUri the base URI without trailing slash
 * @param credentials app credentials, may be {@code null}
 * @param bearer bearer token provider, may be {@code null}
 * @param requestTimeout per-request timeout, may be {@code null} for the transport default
 * @param retryPolicy the retry policy
 * @param userAgent the {@code User-Agent} value
 * @param clock clock used for {@code Retry-After} dates
 */
public record ClientSettings(
        HttpTransport transport,
        URI baseUri,
        AppCredentials credentials,
        BearerTokenProvider bearer,
        Duration requestTimeout,
        RetryPolicy retryPolicy,
        String userAgent,
        Clock clock) {

    @Override
    public String toString() {
        return "ClientSettings[baseUri="
                + baseUri
                + ", app="
                + (credentials != null)
                + ", bearer="
                + (bearer != null)
                + "]";
    }
}
