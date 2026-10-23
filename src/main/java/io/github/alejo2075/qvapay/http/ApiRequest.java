package io.github.alejo2075.qvapay.http;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A fully resolved HTTP request handed to an {@link HttpTransport}: credentials are already part of
 * the headers.
 *
 * <p>{@link #toString()} masks credential headers and prints only the body length.
 */
public final class ApiRequest {

    private static final Set<String> SENSITIVE_HEADERS = Set.of("app-secret", "authorization");

    private final String method;
    private final URI uri;
    private final Map<String, String> headers;
    private final byte[] body;
    private final Duration timeout;

    /**
     * Creates a request.
     *
     * @param method the HTTP method, e.g. {@code POST}
     * @param uri the absolute URI
     * @param headers the request headers
     * @param body the body bytes, or {@code null} for no body
     * @param timeout the per-request timeout, or {@code null} for the transport default
     */
    public ApiRequest(
            String method, URI uri, Map<String, String> headers, byte[] body, Duration timeout) {
        this.method = method;
        this.uri = uri;
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(headers);
        this.headers = java.util.Collections.unmodifiableMap(copy);
        this.body = body == null ? null : body.clone();
        this.timeout = timeout;
    }

    /**
     * Returns the HTTP method.
     *
     * @return the method
     */
    public String method() {
        return method;
    }

    /**
     * Returns the absolute URI.
     *
     * @return the URI
     */
    public URI uri() {
        return uri;
    }

    /**
     * Returns the headers (case-insensitive keys). They may contain credentials.
     *
     * @return an unmodifiable map
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * Returns a copy of the body.
     *
     * @return the body bytes, or {@code null} if there is none
     */
    public byte[] body() {
        return body == null ? null : body.clone();
    }

    /**
     * Returns the request timeout.
     *
     * @return the timeout, or {@code null} to use the transport default
     */
    public Duration timeout() {
        return timeout;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ApiRequest[").append(method).append(' ').append(uri);
        sb.append(", headers={");
        boolean first = true;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            String value =
                    SENSITIVE_HEADERS.contains(e.getKey().toLowerCase(Locale.ROOT))
                            ? "****"
                            : e.getValue();
            sb.append(e.getKey()).append('=').append(value);
        }
        sb.append("}, bodyLength=").append(body == null ? 0 : body.length).append(']');
        return sb.toString();
    }
}
