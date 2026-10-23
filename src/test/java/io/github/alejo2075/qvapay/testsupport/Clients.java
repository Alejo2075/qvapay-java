package io.github.alejo2075.qvapay.testsupport;

import io.github.alejo2075.qvapay.QvaPayClient;

/** Builds clients wired to a {@link FakeTransport}. */
public final class Clients {

    public static final String APP_ID = "550e8400-e29b-41d4-a716-446655440000";
    public static final String APP_SECRET = "test-secret-not-real";

    private Clients() {}

    public static QvaPayClient withApp(FakeTransport transport) {
        return QvaPayClient.builder()
                .appCredentials(APP_ID, APP_SECRET)
                .transport(transport)
                .build();
    }
}
