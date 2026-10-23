package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.exception.AuthenticationException;
import io.github.alejo2075.qvapay.exception.NotFoundException;
import io.github.alejo2075.qvapay.exception.PermissionDeniedException;
import io.github.alejo2075.qvapay.exception.QvaPayApiException;
import io.github.alejo2075.qvapay.exception.RateLimitException;
import io.github.alejo2075.qvapay.exception.ServerException;
import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.http.ApiResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Turns a non-successful {@link ApiResponse} into a typed exception.
 *
 * <p>QvaPay error bodies look like {@code {"error": "..."}}, optionally with a stable {@code code}
 * ({@code {"code": "KYC_REQUIRED", ...}}); some responses use {@code message}. Bodies that are not
 * JSON are never echoed (they may be HTML error pages).
 */
public final class ErrorMapper {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private ErrorMapper() {}

    /**
     * Maps a response to an exception.
     *
     * @param response a response whose status is not 2xx
     * @param clock used to interpret an HTTP-date {@code Retry-After}
     * @return the exception to throw
     */
    public static QvaPayApiException map(ApiResponse response, Clock clock) {
        int status = response.statusCode();
        JsonNode tree = JsonCodec.readTreeOrNull(response.body());
        String code = text(tree, "code");
        String message = text(tree, "error");
        if (message == null) {
            message = text(tree, "message");
        }
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }
        Map<String, String> rate = rateLimitHeaders(response);

        if (status == 400) {
            return new ValidationException(status, code, message, rate);
        } else if (status == 401) {
            return new AuthenticationException(status, code, message, rate);
        } else if (status == 403) {
            return new PermissionDeniedException(status, code, message, rate);
        } else if (status == 404) {
            return new NotFoundException(status, code, message, rate);
        } else if (status == 429) {
            return new RateLimitException(
                    code, message, rate, parseRetryAfter(rate.get("retry-after"), clock));
        } else if (status >= 500 && status < 600) {
            return new ServerException(status, code, message, rate);
        }
        return new QvaPayApiException(status, code, message, rate);
    }

    /**
     * Parses a {@code Retry-After} value (delta seconds or HTTP-date).
     *
     * @param value the header value, may be {@code null}
     * @param clock the clock for HTTP-date values
     * @return the delay, or {@code null} if absent or unparsable
     */
    public static Duration parseRetryAfter(String value, Clock clock) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            long seconds = Long.parseLong(v);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // fall through to HTTP-date
        }
        try {
            ZonedDateTime when = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration d = Duration.between(clock.instant(), when.toInstant());
            return d.isNegative() ? Duration.ZERO : d;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode tree, String field) {
        if (tree == null || !tree.isObject()) {
            return null;
        }
        JsonNode n = tree.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.isString() ? n.asString() : n.toString();
    }

    private static Map<String, String> rateLimitHeaders(ApiResponse response) {
        Map<String, String> out = new LinkedHashMap<>();
        response.headers()
                .forEach(
                        (name, values) -> {
                            if (!values.isEmpty()
                                    && (name.equals("retry-after")
                                            || name.startsWith("x-ratelimit-"))) {
                                out.put(name, values.get(0));
                            }
                        });
        return out;
    }
}
