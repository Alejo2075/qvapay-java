package io.github.alejo2075.qvapay.auth;

/**
 * Supplies the user Bearer token for operations that act on behalf of a user.
 *
 * <p>The SDK never logs in, stores or refreshes tokens by itself. Tokens come from a login flow of
 * your own or from a limited-permission API token generated in the QvaPay web app. The provider is
 * called for every request, so it can return a rotated token.
 */
@FunctionalInterface
public interface BearerTokenProvider {

    /**
     * Returns the current token.
     *
     * @return the token without the {@code Bearer } prefix
     */
    String token();

    /**
     * Creates a provider that always returns the same token.
     *
     * @param token the token
     * @return the provider
     * @throws IllegalArgumentException if the token is null or blank
     */
    static BearerTokenProvider of(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return new BearerTokenProvider() {
            @Override
            public String token() {
                return token;
            }

            @Override
            public String toString() {
                return "BearerTokenProvider[token=****]";
            }
        };
    }
}
