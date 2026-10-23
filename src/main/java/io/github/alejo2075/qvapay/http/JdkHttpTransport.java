package io.github.alejo2075.qvapay.http;

import io.github.alejo2075.qvapay.exception.TransportException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@link HttpTransport} backed by the JDK {@link HttpClient}.
 *
 * <p>Redirects are never followed, so credential headers cannot be forwarded to another host. The
 * instance is thread-safe and meant to be reused.
 */
public final class JdkHttpTransport implements HttpTransport {

    /** Default connect timeout. */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Default request timeout. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client;
    private final Duration defaultRequestTimeout;

    /** Creates a transport with the default timeouts. */
    public JdkHttpTransport() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Creates a transport.
     *
     * @param connectTimeout maximum time to establish a connection
     * @param defaultRequestTimeout request timeout used when a request does not set its own
     */
    public JdkHttpTransport(Duration connectTimeout, Duration defaultRequestTimeout) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                defaultRequestTimeout);
    }

    /**
     * Creates a transport around an existing client. The client should be configured with {@link
     * HttpClient.Redirect#NEVER}.
     *
     * @param client the JDK client
     * @param defaultRequestTimeout request timeout used when a request does not set its own
     */
    public JdkHttpTransport(HttpClient client, Duration defaultRequestTimeout) {
        this.client = client;
        this.defaultRequestTimeout = defaultRequestTimeout;
    }

    @Override
    public ApiResponse send(ApiRequest request) {
        HttpRequest httpRequest = toHttpRequest(request);
        try {
            return toApiResponse(client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(describe(request, "was interrupted"), e);
        } catch (IOException e) {
            throw new TransportException(describe(request, "failed"), e);
        }
    }

    @Override
    public CompletableFuture<ApiResponse> sendAsync(ApiRequest request) {
        HttpRequest httpRequest = toHttpRequest(request);
        return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                .handle(
                        (response, error) -> {
                            if (error == null) {
                                return toApiResponse(response);
                            }
                            Throwable cause =
                                    error instanceof CompletionException && error.getCause() != null
                                            ? error.getCause()
                                            : error;
                            if (cause instanceof CancellationException) {
                                throw (CancellationException) cause;
                            }
                            throw new TransportException(describe(request, "failed"), cause);
                        });
    }

    private HttpRequest toHttpRequest(ApiRequest request) {
        Duration timeout = request.timeout() != null ? request.timeout() : defaultRequestTimeout;
        HttpRequest.Builder b = HttpRequest.newBuilder(request.uri()).timeout(timeout);
        request.headers().forEach(b::header);
        byte[] body = request.body();
        b.method(
                request.method(),
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        return b.build();
    }

    private static ApiResponse toApiResponse(HttpResponse<byte[]> response) {
        return new ApiResponse(response.statusCode(), response.headers().map(), response.body());
    }

    private static String describe(ApiRequest request, String what) {
        URI uri = request.uri();
        return "HTTP request " + request.method() + " " + uri.getRawPath() + " " + what;
    }
}
