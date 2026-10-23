package io.github.alejo2075.qvapay.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.internal.UrlEncoding;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiRequestResponseTest {

    @Test
    void requestToStringMasksCredentialHeadersAndBody() {
        ApiRequest r =
                new ApiRequest(
                        "POST",
                        URI.create("https://api.qvapay.com/v2/info"),
                        Map.of(
                                "app-id",
                                "id-1",
                                "app-secret",
                                "TOPSECRET",
                                "Authorization",
                                "Bearer TOK"),
                        "{\"x\":\"BODYSECRET\"}".getBytes(),
                        null);
        String s = r.toString();
        assertFalse(s.contains("TOPSECRET"));
        assertFalse(s.contains("TOK"));
        assertFalse(s.contains("BODYSECRET"));
        assertTrue(s.contains("id-1"));
        assertTrue(s.contains("bodyLength=18"));
    }

    @Test
    void requestHeadersAreCaseInsensitiveAndBodyIsDefensivelyCopied() {
        byte[] body = {1, 2, 3};
        ApiRequest r =
                new ApiRequest("POST", URI.create("https://x"), Map.of("Accept", "a"), body, null);
        body[0] = 9;
        assertEquals(1, r.body()[0]);
        assertEquals("a", r.headers().get("accept"));
    }

    @Test
    void responseHeaderLookupIsCaseInsensitive() {
        ApiResponse r = new ApiResponse(429, Map.of("Retry-After", List.of("7")), null);
        assertEquals("7", r.header("retry-after"));
        assertEquals("7", r.header("RETRY-AFTER"));
        assertNull(r.header("x-missing"));
        assertFalse(r.isSuccessful());
        assertEquals(0, r.body().length);
        assertTrue(new ApiResponse(204, Map.of(), null).isSuccessful());
    }

    @Test
    void urlEncodingEncodesEverythingExceptUnreserved() {
        assertEquals("abc-._~123", UrlEncoding.encode("abc-._~123"));
        assertEquals("a%2Fb%3Fc%3Dd%26e%20f", UrlEncoding.encode("a/b?c=d&e f"));
        assertEquals("%2E%2E".replace("%2E", "."), UrlEncoding.encode(".."));
        assertEquals("%C3%B1", UrlEncoding.encode("ñ"));
        assertEquals("%25", UrlEncoding.encode("%"));
    }
}
