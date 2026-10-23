package io.github.alejo2075.qvapay.http;

import java.util.concurrent.CompletableFuture;

/**
 * Sends resolved HTTP requests. The default implementation is {@link JdkHttpTransport}; a custom
 * one can be injected into the client builder, for example in tests.
 *
 * <p>Implementations must be thread-safe, must not follow redirects and must not log requests,
 * because requests carry credentials. Non-2xx statuses are returned as responses, not thrown; only
 * network level failures raise {@link io.github.alejo2075.qvapay.exception.TransportException}.
 */
public interface HttpTransport {

    /**
     * Sends a request and waits for the response.
     *
     * @param request the request
     * @return the response
     * @throws io.github.alejo2075.qvapay.exception.TransportException on I/O failure, timeout or
     *     interruption (the interrupt flag is restored)
     */
    ApiResponse send(ApiRequest request);

    /**
     * Sends a request asynchronously.
     *
     * @param request the request
     * @return a future completed with the response, or exceptionally with a {@link
     *     io.github.alejo2075.qvapay.exception.TransportException}
     */
    CompletableFuture<ApiResponse> sendAsync(ApiRequest request);
}
