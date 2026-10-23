package io.github.alejo2075.qvapay.testsupport;

import io.github.alejo2075.qvapay.exception.TransportException;
import io.github.alejo2075.qvapay.http.ApiRequest;
import io.github.alejo2075.qvapay.http.ApiResponse;
import io.github.alejo2075.qvapay.http.HttpTransport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** In-memory {@link HttpTransport}: scripted outcomes, recorded requests, no network. */
public final class FakeTransport implements HttpTransport {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<ApiRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public static ApiResponse json(int status, String body) {
        return new ApiResponse(
                status,
                Map.of("Content-Type", List.of("application/json")),
                body.getBytes(StandardCharsets.UTF_8));
    }

    public static ApiResponse json(int status, String body, Map<String, List<String>> headers) {
        return new ApiResponse(status, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    /** Queues a response (or a {@link RuntimeException} to throw). The last outcome repeats. */
    public FakeTransport thenReturn(Object outcome) {
        script.add(outcome);
        return this;
    }

    public static FakeTransport returning(Object... outcomes) {
        FakeTransport t = new FakeTransport();
        for (Object o : outcomes) {
            t.thenReturn(o);
        }
        return t;
    }

    public static TransportException networkError() {
        return new TransportException(
                "HTTP request POST /x failed", new java.io.IOException("reset"));
    }

    public List<ApiRequest> requests() {
        return new ArrayList<>(requests);
    }

    public ApiRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    private Object next(ApiRequest request) {
        requests.add(request);
        Object o = script.size() > 1 ? script.poll() : script.peek();
        if (o == null) {
            throw new IllegalStateException("no scripted outcome");
        }
        return o;
    }

    @Override
    public ApiResponse send(ApiRequest request) {
        Object o = next(request);
        if (o instanceof RuntimeException) {
            throw (RuntimeException) o;
        }
        return (ApiResponse) o;
    }

    @Override
    public CompletableFuture<ApiResponse> sendAsync(ApiRequest request) {
        Object o = next(request);
        if (o instanceof RuntimeException) {
            return CompletableFuture.failedFuture((RuntimeException) o);
        }
        return CompletableFuture.completedFuture((ApiResponse) o);
    }
}
