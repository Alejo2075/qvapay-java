package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.auth.AuthPolicy;
import io.github.alejo2075.qvapay.exception.RateLimitException;
import io.github.alejo2075.qvapay.exception.ServerException;
import io.github.alejo2075.qvapay.exception.TransportException;
import io.github.alejo2075.qvapay.http.ApiRequest;
import io.github.alejo2075.qvapay.http.ApiResponse;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Builds requests from {@link Operation}s, sends them, maps errors and applies the retry policy.
 *
 * <p>Only the credentials required by the operation's {@link AuthPolicy} are attached, so app
 * credentials and Bearer tokens never leak into each other's requests.
 */
public final class RequestExecutor {

    private final ClientSettings settings;

    /**
     * Creates an executor.
     *
     * @param settings the client settings
     */
    public RequestExecutor(ClientSettings settings) {
        this.settings = settings;
    }

    /**
     * Executes an operation synchronously.
     *
     * @param op the operation
     * @param mapper converts the successful response body into the result
     * @param <T> the result type
     * @return the result
     */
    public <T> T execute(Operation op, Function<byte[], T> mapper) {
        ApiRequest request = buildRequest(op);
        int attempt = 1;
        while (true) {
            Throwable failure;
            try {
                ApiResponse response = settings.transport().send(request);
                if (response.isSuccessful()) {
                    return mapper.apply(response.body());
                }
                failure = ErrorMapper.map(response, settings.clock());
            } catch (TransportException e) {
                failure = e;
            }
            Duration wait = retryDelay(op, attempt, failure);
            if (wait == null || Thread.currentThread().isInterrupted()) {
                throw (RuntimeException) failure;
            }
            sleep(wait, request);
            attempt++;
        }
    }

    /**
     * Executes an operation asynchronously. The returned future completes exceptionally with the
     * typed SDK exception (not wrapped).
     *
     * @param op the operation
     * @param mapper converts the successful response body into the result
     * @param <T> the result type
     * @return the future result
     */
    public <T> CompletableFuture<T> executeAsync(Operation op, Function<byte[], T> mapper) {
        CompletableFuture<T> result = new CompletableFuture<>();
        ApiRequest request;
        try {
            request = buildRequest(op);
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
            return result;
        }
        attemptAsync(op, request, mapper, 1, result);
        return result;
    }

    private <T> void attemptAsync(
            Operation op,
            ApiRequest request,
            Function<byte[], T> mapper,
            int attempt,
            CompletableFuture<T> result) {
        CompletableFuture<ApiResponse> sent;
        try {
            sent = settings.transport().sendAsync(request);
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
            return;
        }
        sent.whenComplete(
                (response, error) -> {
                    try {
                        Throwable failure;
                        if (error != null) {
                            failure = unwrap(error);
                        } else if (response.isSuccessful()) {
                            result.complete(mapper.apply(response.body()));
                            return;
                        } else {
                            failure = ErrorMapper.map(response, settings.clock());
                        }
                        Duration wait = retryDelay(op, attempt, failure);
                        if (wait == null) {
                            result.completeExceptionally(failure);
                            return;
                        }
                        CompletableFuture.delayedExecutor(wait.toMillis(), TimeUnit.MILLISECONDS)
                                .execute(
                                        () ->
                                                attemptAsync(
                                                        op, request, mapper, attempt + 1, result));
                    } catch (Throwable t) {
                        result.completeExceptionally(t);
                    }
                });
    }

    private Duration retryDelay(Operation op, int attempt, Throwable failure) {
        if (!op.safeToRetry() || !settings.retryPolicy().enabled()) {
            return null;
        }
        Duration retryAfter = null;
        if (failure instanceof RateLimitException) {
            retryAfter = ((RateLimitException) failure).retryAfter().orElse(null);
        } else if (!(failure instanceof ServerException || failure instanceof TransportException)) {
            return null;
        }
        return settings.retryPolicy().delayBeforeRetry(attempt, retryAfter);
    }

    private static Throwable unwrap(Throwable t) {
        return (t instanceof CompletionException && t.getCause() != null) ? t.getCause() : t;
    }

    private static void sleep(Duration wait, ApiRequest request) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(
                    "HTTP request "
                            + request.method()
                            + " "
                            + request.uri().getRawPath()
                            + " was interrupted while waiting to retry",
                    e);
        }
    }

    private ApiRequest buildRequest(Operation op) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", settings.userAgent());
        applyAuth(op.auth(), headers);
        byte[] body = null;
        if (op.body() != null) {
            body = JsonCodec.write(op.body());
            headers.put("Content-Type", "application/json");
        }
        return new ApiRequest(op.method(), buildUri(op), headers, body, settings.requestTimeout());
    }

    private void applyAuth(AuthPolicy policy, Map<String, String> headers) {
        switch (policy) {
            case PUBLIC:
                return;
            case APP:
                requireApp();
                putApp(headers);
                return;
            case BEARER:
                requireBearer();
                putBearer(headers);
                return;
            case APP_OR_BEARER:
            default:
                if (settings.credentials() != null) {
                    putApp(headers);
                } else {
                    requireBearer();
                    putBearer(headers);
                }
        }
    }

    private void requireApp() {
        if (settings.credentials() == null) {
            throw new IllegalStateException(
                    "This operation needs app credentials: call appCredentials(...) on the client builder");
        }
    }

    private void requireBearer() {
        if (settings.bearer() == null) {
            throw new IllegalStateException(
                    "This operation needs a Bearer token: call bearerTokenProvider(...) on the client builder");
        }
    }

    private void putApp(Map<String, String> headers) {
        headers.put("app-id", settings.credentials().appId());
        headers.put("app-secret", settings.credentials().appSecret());
    }

    private void putBearer(Map<String, String> headers) {
        String token = settings.bearer().token();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("The BearerTokenProvider returned an empty token");
        }
        headers.put("Authorization", "Bearer " + token);
    }

    private URI buildUri(Operation op) {
        StringBuilder sb = new StringBuilder(settings.baseUri().toString()).append(op.path());
        if (op.query() != null && !op.query().isEmpty()) {
            char sep = '?';
            for (Map.Entry<String, String> e : op.query().entrySet()) {
                sb.append(sep)
                        .append(UrlEncoding.encode(e.getKey()))
                        .append('=')
                        .append(UrlEncoding.encode(e.getValue()));
                sep = '&';
            }
        }
        return URI.create(sb.toString());
    }
}
