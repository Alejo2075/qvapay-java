package io.github.alejo2075.qvapay.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.exception.AuthenticationException;
import io.github.alejo2075.qvapay.exception.NotFoundException;
import io.github.alejo2075.qvapay.exception.PermissionDeniedException;
import io.github.alejo2075.qvapay.exception.QvaPayApiException;
import io.github.alejo2075.qvapay.exception.RateLimitException;
import io.github.alejo2075.qvapay.exception.ServerException;
import io.github.alejo2075.qvapay.exception.ValidationException;
import io.github.alejo2075.qvapay.http.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorMapperTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    private static ApiResponse resp(int status, String body, Map<String, List<String>> headers) {
        return new ApiResponse(status, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @CsvSource({
        "400,ValidationException",
        "401,AuthenticationException",
        "403,PermissionDeniedException",
        "404,NotFoundException",
        "429,RateLimitException",
        "500,ServerException",
        "503,ServerException",
        "418,QvaPayApiException",
        "302,QvaPayApiException"
    })
    void statusMapsToTypedException(int status, String simpleName) {
        QvaPayApiException e = ErrorMapper.map(resp(status, "{\"error\":\"x\"}", Map.of()), CLOCK);
        assertEquals(simpleName, e.getClass().getSimpleName());
        assertEquals(status, e.statusCode());
        assertEquals("x", e.apiMessage());
    }

    @Test
    void readsCodeAndErrorFields() {
        QvaPayApiException e =
                ErrorMapper.map(
                        resp(
                                403,
                                "{\"code\":\"KYC_REQUIRED\",\"error\":\"kyc\",\"kyc_url\":\"/onboarding\"}",
                                Map.of()),
                        CLOCK);
        assertInstanceOf(PermissionDeniedException.class, e);
        assertEquals("KYC_REQUIRED", e.errorCode());
        assertEquals("kyc", e.apiMessage());
    }

    @Test
    void worksWithOnlyErrorAndFallsBackToMessage() {
        assertNull(
                ErrorMapper.map(resp(400, "{\"error\":\"only error\"}", Map.of()), CLOCK)
                        .errorCode());
        assertEquals(
                "msg",
                ErrorMapper.map(resp(400, "{\"message\":\"msg\"}", Map.of()), CLOCK).apiMessage());
    }

    @Test
    void nonJsonBodiesAreNeverEchoed() {
        QvaPayApiException e =
                ErrorMapper.map(resp(502, "<html>secret internal page</html>", Map.of()), CLOCK);
        assertInstanceOf(ServerException.class, e);
        assertNull(e.apiMessage());
        assertFalse(e.getMessage().contains("secret"));
        assertNull(ErrorMapper.map(resp(404, "", Map.of()), CLOCK).apiMessage());
    }

    @Test
    void longMessagesAreTruncated() {
        String big = "a".repeat(2000);
        QvaPayApiException e =
                ErrorMapper.map(resp(400, "{\"error\":\"" + big + "\"}", Map.of()), CLOCK);
        assertTrue(e.apiMessage().length() < 600);
    }

    @Test
    void rateLimitCarriesRetryAfterSecondsAndHeaders() {
        RateLimitException e =
                (RateLimitException)
                        ErrorMapper.map(
                                resp(
                                        429,
                                        "{\"error\":\"slow\"}",
                                        Map.of(
                                                "Retry-After",
                                                List.of("7"),
                                                "X-RateLimit-Remaining",
                                                List.of("0"),
                                                "Set-Cookie",
                                                List.of("s=1"))),
                                CLOCK);
        assertEquals(Duration.ofSeconds(7), e.retryAfter().orElseThrow());
        assertEquals("0", e.rateLimitHeaders().get("x-ratelimit-remaining"));
        assertFalse(e.rateLimitHeaders().containsKey("set-cookie"));
    }

    @Test
    void retryAfterAcceptsHttpDateAndIgnoresGarbage() {
        assertEquals(
                Duration.ofSeconds(30),
                ErrorMapper.parseRetryAfter("Thu, 22 Oct 2026 12:00:30 GMT", CLOCK));
        assertEquals(
                Duration.ZERO, ErrorMapper.parseRetryAfter("Thu, 22 Oct 2026 11:00:00 GMT", CLOCK));
        assertNull(ErrorMapper.parseRetryAfter("soon", CLOCK));
        assertNull(ErrorMapper.parseRetryAfter("-5", CLOCK));
        assertNull(ErrorMapper.parseRetryAfter(null, CLOCK));
    }

    @Test
    void unauthorizedAndNotFoundTypes() {
        assertInstanceOf(
                AuthenticationException.class, ErrorMapper.map(resp(401, "{}", Map.of()), CLOCK));
        assertInstanceOf(
                NotFoundException.class, ErrorMapper.map(resp(404, "{}", Map.of()), CLOCK));
        assertInstanceOf(
                ValidationException.class, ErrorMapper.map(resp(400, "{}", Map.of()), CLOCK));
    }
}
