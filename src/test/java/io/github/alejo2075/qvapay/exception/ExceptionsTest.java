package io.github.alejo2075.qvapay.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExceptionsTest {

    @Test
    void apiExceptionExposesStatusCodeAndMessageParts() {
        QvaPayApiException e =
                new ValidationException(
                        400, "DUPLICATE_REQUEST", "dup", Map.of("retry-after", "3"));
        assertEquals(400, e.statusCode());
        assertEquals("DUPLICATE_REQUEST", e.errorCode());
        assertEquals("dup", e.apiMessage());
        assertEquals("3", e.rateLimitHeaders().get("retry-after"));
        assertTrue(e.getMessage().contains("HTTP 400"));
        assertTrue(e.getMessage().contains("code=DUPLICATE_REQUEST"));
    }

    @Test
    void errorCodeMayBeAbsent() {
        QvaPayApiException e = new NotFoundException(404, null, "missing", null);
        assertNull(e.errorCode());
        assertTrue(e.rateLimitHeaders().isEmpty());
        assertEquals("QvaPay API error (HTTP 404): missing", e.getMessage());
    }

    @Test
    void rateLimitExceptionCarriesRetryAfter() {
        RateLimitException e =
                new RateLimitException(null, "slow down", Map.of(), Duration.ofSeconds(5));
        assertEquals(429, e.statusCode());
        assertEquals(Duration.ofSeconds(5), e.retryAfter().orElseThrow());
        assertTrue(new RateLimitException(null, null, Map.of(), null).retryAfter().isEmpty());
    }

    @Test
    void hierarchyIsUnchecked() {
        assertTrue(RuntimeException.class.isAssignableFrom(QvaPayException.class));
        assertTrue(QvaPayException.class.isAssignableFrom(TransportException.class));
        assertTrue(QvaPayException.class.isAssignableFrom(SerializationException.class));
        assertTrue(QvaPayApiException.class.isAssignableFrom(ServerException.class));
        assertTrue(QvaPayApiException.class.isAssignableFrom(AuthenticationException.class));
        assertTrue(QvaPayApiException.class.isAssignableFrom(PermissionDeniedException.class));
    }
}
