package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.auth.AuthPolicy;
import java.util.Map;

/**
 * Description of one API operation, independent of any client configuration.
 *
 * @param method the HTTP method
 * @param path the already percent-encoded path, starting with {@code /}
 * @param query query parameters (raw values, encoded when the URI is built), may be {@code null}
 * @param auth which credentials to send
 * @param body JSON body as an ordered map ({@code null} value = explicit JSON null, missing key =
 *     absent field), or {@code null} for no body
 * @param safeToRetry {@code true} only for read-only operations
 */
public record Operation(
        String method,
        String path,
        Map<String, String> query,
        AuthPolicy auth,
        Map<String, Object> body,
        boolean safeToRetry) {

    /**
     * A {@code POST} with a JSON body (an empty object when there are no parameters).
     *
     * @param path the encoded path
     * @param body the body fields, or {@code null} for {@code {}}
     * @param auth the auth policy
     * @param safeToRetry whether the operation is read-only
     * @return the operation
     */
    public static Operation post(
            String path, Map<String, Object> body, AuthPolicy auth, boolean safeToRetry) {
        return new Operation("POST", path, null, auth, body == null ? Map.of() : body, safeToRetry);
    }
}
