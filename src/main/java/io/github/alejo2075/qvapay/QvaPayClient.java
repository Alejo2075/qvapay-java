package io.github.alejo2075.qvapay;

import io.github.alejo2075.qvapay.auth.AppCredentials;
import io.github.alejo2075.qvapay.auth.BearerTokenProvider;
import io.github.alejo2075.qvapay.http.HttpTransport;
import io.github.alejo2075.qvapay.http.JdkHttpTransport;
import io.github.alejo2075.qvapay.http.RetryPolicy;
import io.github.alejo2075.qvapay.internal.ClientSettings;
import io.github.alejo2075.qvapay.internal.RequestExecutor;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Entry point of the unofficial QvaPay SDK.
 *
 * <p>A client is immutable and thread-safe: create it once and share it. Each client keeps its own
 * credentials, so several clients with different apps can live side by side.
 *
 * <pre>{@code
 * QvaPayClient client = QvaPayClient.builder()
 *     .appCredentials(System.getenv("QVAPAY_APP_ID"), System.getenv("QVAPAY_APP_SECRET"))
 *     .build();
 * }</pre>
 *
 * <p><strong>Never</strong> use app credentials in a browser or mobile app: they belong on your
 * server.
 */
public final class QvaPayClient {

    /** Production base URL of the QvaPay API. */
    public static final String DEFAULT_BASE_URL = "https://api.qvapay.com";

    private final ClientSettings settings;
    private final RequestExecutor executor;
    private final MerchantApi merchant;
    private final InvoiceApi invoices;

    private QvaPayClient(ClientSettings settings) {
        this.settings = settings;
        this.executor = new RequestExecutor(settings);
        this.merchant = new MerchantApi(executor);
        this.invoices = new InvoiceApi(executor);
    }

    /**
     * Starts building a client.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the application level operations ({@code info}, {@code balance}).
     *
     * @return the merchant API
     */
    public MerchantApi merchant() {
        return merchant;
    }

    /**
     * Returns the invoice operations (create, modify).
     *
     * @return the invoice API
     */
    public InvoiceApi invoices() {
        return invoices;
    }

    /**
     * Returns the effective base URL (without trailing slash).
     *
     * @return the base URL
     */
    public String baseUrl() {
        return settings.baseUri().toString();
    }

    RequestExecutor executor() {
        return executor;
    }

    ClientSettings settings() {
        return settings;
    }

    @Override
    public String toString() {
        return "QvaPayClient[" + settings + "]";
    }

    /** Builder for {@link QvaPayClient}. Not thread-safe; use it from one thread. */
    public static final class Builder {

        private AppCredentials credentials;
        private BearerTokenProvider bearer;
        private String baseUrl = DEFAULT_BASE_URL;
        private boolean allowInsecure;
        private Duration connectTimeout = JdkHttpTransport.DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = JdkHttpTransport.DEFAULT_REQUEST_TIMEOUT;
        private RetryPolicy retryPolicy = RetryPolicy.disabled();
        private HttpTransport transport;
        private Clock clock = Clock.systemUTC();

        private Builder() {}

        /**
         * Sets the app credentials used by merchant ({@code /v2/*}) operations.
         *
         * @param credentials the credentials
         * @return this builder
         */
        public Builder appCredentials(AppCredentials credentials) {
            this.credentials = Objects.requireNonNull(credentials, "credentials");
            return this;
        }

        /**
         * Sets the app credentials used by merchant ({@code /v2/*}) operations.
         *
         * @param appId the application UUID
         * @param appSecret the application secret
         * @return this builder
         * @throws IllegalArgumentException if a value is blank
         */
        public Builder appCredentials(String appId, String appSecret) {
            return appCredentials(AppCredentials.of(appId, appSecret));
        }

        /**
         * Sets the provider of the user Bearer token, for operations that act on behalf of a user.
         *
         * @param provider the provider
         * @return this builder
         */
        public Builder bearerTokenProvider(BearerTokenProvider provider) {
            this.bearer = Objects.requireNonNull(provider, "provider");
            return this;
        }

        /**
         * Overrides the base URL. Meant for tests and controlled environments; it must be HTTPS
         * unless {@link #allowInsecureBaseUrlForTesting()} is called.
         *
         * @param baseUrl the base URL, e.g. {@code https://api.qvapay.com}
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        /**
         * Allows a plain {@code http://} base URL. <strong>Only for local tests</strong> against a
         * mock server: credentials would travel unencrypted.
         *
         * @return this builder
         */
        public Builder allowInsecureBaseUrlForTesting() {
            this.allowInsecure = true;
            return this;
        }

        /**
         * Sets the connect timeout of the default transport (default 5 seconds).
         *
         * @param timeout a positive duration
         * @return this builder
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = positive(timeout, "connectTimeout");
            return this;
        }

        /**
         * Sets the request timeout (default 15 seconds).
         *
         * @param timeout a positive duration
         * @return this builder
         */
        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = positive(timeout, "requestTimeout");
            return this;
        }

        /**
         * Enables retries for read-only operations. Disabled by default. Operations that create
         * resources or move money are never retried.
         *
         * @param policy the policy
         * @return this builder
         */
        public Builder retryPolicy(RetryPolicy policy) {
            this.retryPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /**
         * Replaces the HTTP transport, e.g. with a test double. When set, the connect timeout is
         * ignored.
         *
         * @param transport the transport
         * @return this builder
         */
        public Builder transport(HttpTransport transport) {
            this.transport = Objects.requireNonNull(transport, "transport");
            return this;
        }

        /**
         * Sets the clock used to interpret HTTP-date {@code Retry-After} headers.
         *
         * @param clock the clock
         * @return this builder
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client
         * @throws IllegalArgumentException if the base URL is invalid or insecure
         */
        public QvaPayClient build() {
            URI base = validateBaseUrl(baseUrl, allowInsecure);
            HttpTransport t =
                    transport != null
                            ? transport
                            : new JdkHttpTransport(connectTimeout, requestTimeout);
            ClientSettings settings =
                    new ClientSettings(
                            t,
                            base,
                            credentials,
                            bearer,
                            requestTimeout,
                            retryPolicy,
                            "qvapay-java/" + QvaPayVersion.get(),
                            clock);
            return new QvaPayClient(settings);
        }

        private static Duration positive(Duration d, String name) {
            Objects.requireNonNull(d, name);
            if (d.isZero() || d.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return d;
        }

        private static URI validateBaseUrl(String raw, boolean allowInsecure) {
            URI uri;
            try {
                uri = new URI(raw.trim());
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("baseUrl is not a valid URL");
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean https = scheme.equals("https");
            if (!https && !(allowInsecure && scheme.equals("http"))) {
                throw new IllegalArgumentException(
                        "baseUrl must use https (plain http is only allowed with allowInsecureBaseUrlForTesting())");
            }
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "baseUrl must have a host and no user info, query or fragment");
            }
            String s = uri.toString();
            while (s.endsWith("/")) {
                s = s.substring(0, s.length() - 1);
            }
            return URI.create(s);
        }
    }
}
