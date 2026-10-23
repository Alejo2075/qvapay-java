package io.github.alejo2075.qvapay.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Credentials of a QvaPay application ({@code app-id} and {@code app-secret}).
 *
 * <p>They are sent as HTTP headers on merchant ({@code /v2/*}) operations, never in a request body.
 * The secret is shown only once when the application is created: keep it in an environment variable
 * or a secret manager and never ship it to a browser or mobile client.
 *
 * <p>{@link #toString()} redacts the secret.
 */
public final class AppCredentials {

    private final String appId;
    private final String appSecret;

    private AppCredentials(String appId, String appSecret) {
        this.appId = requireText(appId, "appId");
        this.appSecret = requireText(appSecret, "appSecret");
    }

    /**
     * Creates credentials.
     *
     * @param appId the application UUID
     * @param appSecret the application secret
     * @return the credentials
     * @throws IllegalArgumentException if a value is null or blank
     */
    public static AppCredentials of(String appId, String appSecret) {
        return new AppCredentials(appId, appSecret);
    }

    /**
     * Reads credentials from the {@code QVAPAY_APP_ID} and {@code QVAPAY_APP_SECRET} environment
     * variables.
     *
     * @return the credentials
     * @throws IllegalStateException if a variable is missing or blank
     */
    public static AppCredentials fromEnvironment() {
        String id = System.getenv("QVAPAY_APP_ID");
        String secret = System.getenv("QVAPAY_APP_SECRET");
        if (id == null || id.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "QVAPAY_APP_ID and QVAPAY_APP_SECRET environment variables must be set");
        }
        return new AppCredentials(id, secret);
    }

    /**
     * Returns the application UUID.
     *
     * @return the app id
     */
    public String appId() {
        return appId;
    }

    /**
     * Returns the secret. Handle with care: do not log it.
     *
     * @return the app secret
     */
    public String appSecret() {
        return appSecret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppCredentials)) {
            return false;
        }
        AppCredentials other = (AppCredentials) o;
        return appId.equals(other.appId)
                && MessageDigest.isEqual(
                        appSecret.getBytes(StandardCharsets.UTF_8),
                        other.appSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId);
    }

    @Override
    public String toString() {
        return "AppCredentials[appId=" + appId + ", appSecret=****]";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
