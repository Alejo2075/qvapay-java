package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.auth.BearerTokenProvider;
import io.github.alejo2075.qvapay.testsupport.TestServer;
import io.github.alejo2075.qvapay.testsupport.TestServer.Stub;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ClientIsolationTest {

    @Test
    void concurrentClientsWithDifferentAppsNeverMixCredentials() {
        try (TestServer server = TestServer.start(Stub.json(200, "{\"balance\":1}"))) {
            String url = server.baseUri().toString();
            QvaPayClient a =
                    QvaPayClient.builder()
                            .baseUrl(url)
                            .allowInsecureBaseUrlForTesting()
                            .appCredentials("app-A", "secret-A")
                            .build();
            QvaPayClient b =
                    QvaPayClient.builder()
                            .baseUrl(url)
                            .allowInsecureBaseUrlForTesting()
                            .appCredentials("app-B", "secret-B")
                            .build();

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futures.add(a.merchant().balanceAsync());
                futures.add(b.merchant().balanceAsync());
                futures.add(CompletableFuture.runAsync(() -> a.merchant().balance()));
                futures.add(CompletableFuture.runAsync(() -> b.merchant().balance()));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();

            int fromA = 0;
            int fromB = 0;
            for (var r : server.requests()) {
                String id = r.header("app-id");
                String secret = r.header("app-secret");
                if ("app-A".equals(id)) {
                    assertEquals("secret-A", secret);
                    fromA++;
                } else {
                    assertEquals("app-B", id);
                    assertEquals("secret-B", secret);
                    fromB++;
                }
            }
            assertEquals(100, fromA);
            assertEquals(100, fromB);
        }
    }

    @Test
    void appOperationsNeverSendTheBearerTokenEvenIfConfigured() {
        try (TestServer server = TestServer.start(Stub.json(200, "{\"balance\":1}"))) {
            QvaPayClient c =
                    QvaPayClient.builder()
                            .baseUrl(server.baseUri().toString())
                            .allowInsecureBaseUrlForTesting()
                            .appCredentials("app-A", "secret-A")
                            .bearerTokenProvider(BearerTokenProvider.of("tok"))
                            .build();
            c.merchant().balance();
            var r = server.requests().get(0);
            assertNull(r.header("authorization"));
            assertEquals("app-A", r.header("app-id"));
        }
    }

    @Test
    void aClientWithoutAppCredentialsFailsLocallyAndSendsNothing() {
        try (TestServer server = TestServer.start(Stub.json(200, "{}"))) {
            QvaPayClient c =
                    QvaPayClient.builder()
                            .baseUrl(server.baseUri().toString())
                            .allowInsecureBaseUrlForTesting()
                            .bearerTokenProvider(BearerTokenProvider.of("tok"))
                            .build();
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, () -> c.merchant().balance());
            assertTrue(server.requests().isEmpty());
        }
    }
}
