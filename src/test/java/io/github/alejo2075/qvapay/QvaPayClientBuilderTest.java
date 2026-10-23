package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class QvaPayClientBuilderTest {

    @Test
    void defaultsToProductionBaseUrl() {
        assertEquals("https://api.qvapay.com", QvaPayClient.builder().build().baseUrl());
    }

    @Test
    void trailingSlashesAreRemoved() {
        assertEquals(
                "https://example.test/base",
                QvaPayClient.builder().baseUrl("https://example.test/base//").build().baseUrl());
    }

    @Test
    void plainHttpIsRejectedUnlessExplicitlyAllowed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QvaPayClient.builder().baseUrl("http://127.0.0.1:8080").build());
        assertEquals(
                "http://127.0.0.1:8080",
                QvaPayClient.builder()
                        .baseUrl("http://127.0.0.1:8080")
                        .allowInsecureBaseUrlForTesting()
                        .build()
                        .baseUrl());
    }

    @Test
    void otherSchemesAndOddUrlsAreRejectedEvenWhenInsecureIsAllowed() {
        for (String bad :
                new String[] {
                    "ftp://x.test",
                    "file:///etc/passwd",
                    "https://user:pw@x.test",
                    "https://x.test?a=1",
                    "https://x.test#f",
                    "not a url",
                    "https:///nohost",
                    "api.qvapay.com"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            QvaPayClient.builder()
                                    .baseUrl(bad)
                                    .allowInsecureBaseUrlForTesting()
                                    .build(),
                    bad);
        }
    }

    @Test
    void invalidUrlMessageNeverEchoesTheValue() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                QvaPayClient.builder()
                                        .baseUrl("https://user:topsecret@x.test")
                                        .build());
        assertFalse(e.getMessage().contains("topsecret"));
    }

    @Test
    void timeoutsMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> QvaPayClient.builder().requestTimeout(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> QvaPayClient.builder().connectTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void userAgentCarriesTheSdkVersion() {
        QvaPayClient c = QvaPayClient.builder().transport(new FakeTransport()).build();
        assertTrue(c.settings().userAgent().startsWith("qvapay-java/"));
        assertFalse(c.settings().userAgent().contains("unknown"));
    }

    @Test
    void toStringDoesNotLeakCredentials() {
        QvaPayClient c =
                QvaPayClient.builder()
                        .appCredentials("app-1", "TOP-SECRET")
                        .bearerTokenProvider(() -> "TOKEN-XYZ")
                        .build();
        assertFalse(c.toString().contains("TOP-SECRET"));
        assertFalse(c.toString().contains("TOKEN-XYZ"));
        assertFalse(c.settings().toString().contains("TOP-SECRET"));
    }
}
