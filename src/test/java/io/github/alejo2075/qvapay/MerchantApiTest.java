package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.exception.AuthenticationException;
import io.github.alejo2075.qvapay.model.AppInfo;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class MerchantApiTest {

    @Test
    void infoUsesPostV2InfoWithAppHeadersAndParsesDirectObject() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/info.json")));
        AppInfo info = Clients.withApp(t).merchant().info();

        var req = t.lastRequest();
        assertEquals("POST", req.method());
        assertEquals("https://api.qvapay.com/v2/info", req.uri().toString());
        assertEquals(Clients.APP_ID, req.headers().get("app-id"));
        assertEquals(Clients.APP_SECRET, req.headers().get("app-secret"));
        assertEquals(null, req.headers().get("Authorization"));

        assertEquals("550e8400-e29b-41d4-a716-446655440000", info.uuid());
        assertEquals("Mi Tienda", info.name());
        assertEquals("https://mitienda.com/success", info.successUrl());
        assertEquals("https://mitienda.com/cancel", info.cancelUrl());
        assertEquals("https://media.qvapay.com/apps/logo.png", info.appPhotoUrl());
        assertTrue(info.active());
        assertTrue(info.enabled());
        assertFalse(info.card());
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), info.createdAt());
        assertEquals(Instant.parse("2024-06-20T14:00:00Z"), info.updatedAt());
    }

    @Test
    void infoAsyncReturnsTheSameData() throws Exception {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/info.json")));
        assertEquals("Mi Tienda", Clients.withApp(t).merchant().infoAsync().get().name());
    }

    @Test
    void invalidCredentialsRaiseAuthenticationException() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(401, "{\"error\":\"Invalid credentials\"}"));
        assertThrows(AuthenticationException.class, () -> Clients.withApp(t).merchant().info());
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> Clients.withApp(t).merchant().infoAsync().get());
        assertInstanceOf(AuthenticationException.class, e.getCause());
    }

    @Test
    void unknownResponseFieldsAreIgnored() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, "{\"uuid\":\"u\",\"new_field\":123}"));
        assertEquals("u", Clients.withApp(t).merchant().info().uuid());
    }
}
