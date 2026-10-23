package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alejo2075.qvapay.exception.AuthenticationException;
import io.github.alejo2075.qvapay.model.AuthorizePaymentsRequest;
import io.github.alejo2075.qvapay.model.ChargeRequest;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.ModifyInvoiceRequest;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import io.github.alejo2075.qvapay.testsupport.TestServer;
import io.github.alejo2075.qvapay.testsupport.TestServer.Stub;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Runs every merchant operation through the real JDK HTTP transport against a loopback server and
 * checks that the blocking and the {@code ...Async} form produce the same request and result.
 */
class SyncAsyncParityTest {

    private static final String UUID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    private static Stub route(TestServer.Recorded r) {
        switch (r.rawPath()) {
            case "/v2/info":
                return Stub.json(200, Fixtures.load("merchant/info.json"));
            case "/v2/balance":
                return Stub.json(200, Fixtures.load("merchant/balance.json"));
            case "/v2/create_invoice":
            case "/v2/modify_invoice":
                return Stub.json(200, Fixtures.load("merchant/invoice.json"));
            case "/v2/transactions":
                return Stub.json(200, Fixtures.load("merchant/transactions.json"));
            case "/v2/transactions/" + UUID:
                return Stub.json(200, Fixtures.load("merchant/transaction-detail.json"));
            case "/v2/authorize_payments":
                return Stub.json(200, Fixtures.load("merchant/authorize-payments.json"));
            case "/v2/charge":
                return Stub.json(200, Fixtures.load("merchant/charge.json"));
            default:
                return Stub.json(404, "{\"error\":\"no route\"}");
        }
    }

    private static QvaPayClient client(TestServer server) {
        return QvaPayClient.builder()
                .baseUrl(server.baseUri().toString())
                .allowInsecureBaseUrlForTesting()
                .appCredentials(Clients.APP_ID, Clients.APP_SECRET)
                .build();
    }

    private record Case(
            String name,
            Function<QvaPayClient, Object> sync,
            Function<QvaPayClient, CompletableFuture<?>> async) {}

    private static List<Case> cases() {
        CreateInvoiceRequest create =
                CreateInvoiceRequest.builder()
                        .amount(new BigDecimal("25.00"))
                        .description("d")
                        .remoteId("r")
                        .build();
        ModifyInvoiceRequest modify =
                ModifyInvoiceRequest.builder(UUID).amount(new BigDecimal("30.00")).build();
        AuthorizePaymentsRequest authorize =
                new AuthorizePaymentsRequest("user-001", "https://example.test/cb");
        ChargeRequest charge =
                new ChargeRequest(new BigDecimal("10.00"), "user-uuid", "d", "sub-001");
        return List.of(
                new Case("info", c -> c.merchant().info(), c -> c.merchant().infoAsync()),
                new Case("balance", c -> c.merchant().balance(), c -> c.merchant().balanceAsync()),
                new Case(
                        "create",
                        c -> c.invoices().create(create),
                        c -> c.invoices().createAsync(create)),
                new Case(
                        "modify",
                        c -> c.invoices().modify(modify),
                        c -> c.invoices().modifyAsync(modify)),
                new Case(
                        "list",
                        c -> c.transactions().list(1, 30),
                        c -> c.transactions().listAsync(1, 30)),
                new Case(
                        "get",
                        c -> c.transactions().get(UUID),
                        c -> c.transactions().getAsync(UUID)),
                new Case(
                        "authorize",
                        c -> c.payments().authorize(authorize),
                        c -> c.payments().authorizeAsync(authorize)),
                new Case(
                        "charge",
                        c -> c.payments().charge(charge),
                        c -> c.payments().chargeAsync(charge)));
    }

    @Test
    void everyOperationReturnsTheSameResultSyncAndAsyncOverRealHttp() throws Exception {
        for (Case c : cases()) {
            try (TestServer server = TestServer.start(SyncAsyncParityTest::route)) {
                QvaPayClient client = client(server);
                Object syncResult = c.sync().apply(client);
                Object asyncResult = c.async().apply(client).get();
                assertEquals(syncResult, asyncResult, c.name());

                var reqs = server.requests();
                assertEquals(2, reqs.size(), c.name());
                assertEquals(reqs.get(0).method(), reqs.get(1).method(), c.name());
                assertEquals(reqs.get(0).rawPath(), reqs.get(1).rawPath(), c.name());
                assertEquals(reqs.get(0).bodyText(), reqs.get(1).bodyText(), c.name());
                assertEquals("POST", reqs.get(0).method(), c.name());
                assertEquals(Clients.APP_ID, reqs.get(0).header("app-id"), c.name());
                assertEquals(Clients.APP_SECRET, reqs.get(0).header("app-secret"), c.name());
                assertEquals("application/json", reqs.get(0).header("content-type"), c.name());
                assertEquals(
                        true,
                        reqs.get(0).header("user-agent").startsWith("qvapay-java/"),
                        c.name());
            }
        }
    }

    @Test
    void everyOperationReportsTheSameTypedErrorSyncAndAsync() {
        for (Case c : cases()) {
            try (TestServer server =
                    TestServer.start(r -> Stub.json(401, "{\"error\":\"Invalid credentials\"}"))) {
                QvaPayClient client = client(server);
                AuthenticationException syncError =
                        assertThrows(
                                AuthenticationException.class,
                                () -> c.sync().apply(client),
                                c.name());
                ExecutionException asyncError =
                        assertThrows(
                                ExecutionException.class,
                                () -> c.async().apply(client).get(),
                                c.name());
                assertInstanceOf(AuthenticationException.class, asyncError.getCause(), c.name());
                assertEquals(
                        syncError.statusCode(),
                        ((AuthenticationException) asyncError.getCause()).statusCode(),
                        c.name());
                assertEquals("Invalid credentials", syncError.apiMessage());
            }
        }
    }

    @Test
    void asyncCallsRunConcurrentlyWithoutBlockingTheCaller() throws Exception {
        try (TestServer server =
                TestServer.start(
                        r ->
                                Stub.json(200, Fixtures.load("merchant/balance.json"))
                                        .delayed(1000))) {
            QvaPayClient client = client(server);
            client.merchant().balance(); // warm up class loading and the HTTP client
            long start = System.nanoTime();
            List<CompletableFuture<?>> futures =
                    List.of(
                            client.merchant().balanceAsync(),
                            client.merchant().balanceAsync(),
                            client.merchant().balanceAsync(),
                            client.merchant().balanceAsync());
            long submitted = (System.nanoTime() - start) / 1_000_000;
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get();
            long total = (System.nanoTime() - start) / 1_000_000;
            // Sequential execution would need 4 x 1000 ms = 4000 ms.
            assertEquals(
                    true,
                    submitted < 700,
                    "submission must not wait for the responses: " + submitted + "ms");
            assertEquals(true, total < 3300, "four 1000ms calls should overlap: " + total + "ms");
            assertEquals(5, server.requests().size());
        }
    }
}
