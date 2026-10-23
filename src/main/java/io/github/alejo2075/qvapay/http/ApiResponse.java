package io.github.alejo2075.qvapay.http;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** A raw HTTP response returned by an {@link HttpTransport}. */
public final class ApiResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    /**
     * Creates a response.
     *
     * @param statusCode the HTTP status code
     * @param headers the response headers
     * @param body the body bytes, may be {@code null} or empty
     */
    public ApiResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        Map<String, List<String>> normalized = new TreeMap<>();
        if (headers != null) {
            headers.forEach(
                    (k, v) -> {
                        if (k != null) {
                            normalized.put(k.toLowerCase(Locale.ROOT), List.copyOf(v));
                        }
                    });
        }
        this.headers = Collections.unmodifiableMap(normalized);
        this.body = body == null ? new byte[0] : body.clone();
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns whether the status is in the 2xx range.
     *
     * @return {@code true} for 2xx
     */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Returns all headers with lower-case names.
     *
     * @return an unmodifiable map
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * Returns the first value of a header.
     *
     * @param name the header name, case-insensitive
     * @return the value, or {@code null} if absent
     */
    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Returns a copy of the body.
     *
     * @return the body bytes, never {@code null}
     */
    public byte[] body() {
        return body.clone();
    }

    @Override
    public String toString() {
        return "ApiResponse[status=" + statusCode + ", bodyLength=" + body.length + "]";
    }
}
