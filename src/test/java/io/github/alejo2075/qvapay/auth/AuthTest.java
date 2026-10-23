package io.github.alejo2075.qvapay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthTest {

    @Test
    void toStringNeverContainsTheSecret() {
        AppCredentials c = AppCredentials.of("app-uuid", "super-secret-value");
        assertFalse(c.toString().contains("super-secret-value"));
        assertTrue(c.toString().contains("app-uuid"));
    }

    @Test
    void equalityConsidersBothFields() {
        AppCredentials a = AppCredentials.of("id", "s1");
        assertEquals(a, AppCredentials.of("id", "s1"));
        assertEquals(a.hashCode(), AppCredentials.of("id", "s1").hashCode());
        assertNotEquals(a, AppCredentials.of("id", "s2"));
        assertNotEquals(a, AppCredentials.of("other", "s1"));
    }

    @Test
    void blankValuesAreRejectedWithoutEchoingThem() {
        assertThrows(IllegalArgumentException.class, () -> AppCredentials.of(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> AppCredentials.of("x", null));
        assertThrows(IllegalArgumentException.class, () -> BearerTokenProvider.of(""));
    }

    @Test
    void bearerProviderReturnsTokenButRedactsToString() {
        BearerTokenProvider p = BearerTokenProvider.of("tok-123");
        assertEquals("tok-123", p.token());
        assertFalse(p.toString().contains("tok-123"));
    }
}
