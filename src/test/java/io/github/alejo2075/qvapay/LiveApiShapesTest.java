package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.alejo2075.qvapay.exception.SerializationException;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Response shapes observed against the real API in test mode on 2026-10-23 (identifiers replaced).
 * They differ from the written documentation, so the SDK must accept both.
 */
class LiveApiShapesTest {

    private static BigDecimal balanceOf(String body) {
        return Clients.withApp(FakeTransport.returning(FakeTransport.json(200, body)))
                .merchant()
                .balance()
                .balance();
    }

    @Test
    void balanceMayBeABareNumber() {
        assertEquals(
                new BigDecimal("25"), balanceOf(Fixtures.load("live/balance-bare-number.json")));
        assertEquals(new BigDecimal("150.75"), balanceOf("150.75"));
        assertEquals(new BigDecimal("0.10"), balanceOf("0.10"), "scale is preserved");
        assertEquals(new BigDecimal("12.50"), balanceOf("\"12.50\""));
    }

    @Test
    void balanceStillAcceptsTheDocumentedObjectForm() {
        assertEquals(new BigDecimal("150.75"), balanceOf("{ \"balance\": 150.75 }"));
        assertEquals(new BigDecimal("3.50"), balanceOf("{\"balance\":\"3.50\"}"));
    }

    @Test
    void unusableBalancesAreSerializationExceptions() {
        for (String bad :
                new String[] {
                    "", "null", "abc", "{}", "{\"balance\":null}", "[1]", "{\"balance\":\"x\"}"
                }) {
            assertThrows(SerializationException.class, () -> balanceOf(bad), bad);
        }
    }

    @Test
    void balanceAsyncAcceptsTheBareNumberToo() throws Exception {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "25"));
        assertEquals(
                new BigDecimal("25"), Clients.withApp(t).merchant().balanceAsync().get().balance());
    }

    @Test
    void testModeInvoiceHasNoUrlAndCarriesExtraFields() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(
                                200, Fixtures.load("live/create-invoice-test-mode.json")));
        var invoice =
                Clients.withApp(t)
                        .invoices()
                        .create(
                                CreateInvoiceRequest.builder()
                                        .amount(new BigDecimal("1.00"))
                                        .description("d")
                                        .remoteId("r")
                                        .build());
        assertNull(invoice.url(), "test mode returns no payment page");
        assertEquals("fd6be71f-1bdf-4a84-ada2-a494d15928b5", invoice.transactionUuid());
        assertEquals(new BigDecimal("1"), invoice.amount());
        assertEquals(TransactionStatus.PAID, invoice.status());
        assertEquals(Boolean.TRUE, invoice.test());
        assertEquals("TEST-fd6be71f-1bdf-4a84-ada2-a494d15928b5", invoice.reference());
        assertEquals(new BigDecimal("26"), invoice.testBalance());
    }

    @Test
    void productionStyleInvoiceLeavesTheTestFieldsNull() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/invoice.json")));
        var invoice =
                Clients.withApp(t)
                        .invoices()
                        .create(
                                CreateInvoiceRequest.builder()
                                        .amount(new BigDecimal("25.00"))
                                        .description("d")
                                        .remoteId("r")
                                        .build());
        assertTrue(invoice.url().startsWith("https://www.qvapay.com/pay/"));
        assertNull(invoice.status());
        assertNull(invoice.test());
        assertNull(invoice.reference());
        assertNull(invoice.testBalance());
    }

    @Test
    void transactionDetailWithStringAmountAndNullPayer() {
        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(
                                200, Fixtures.load("live/transaction-detail-test-mode.json")));
        var tx =
                Clients.withApp(t)
                        .transactions()
                        .get("c0c22f20-a2ec-47a8-8225-4f61aae0f0ed")
                        .transaction();
        assertEquals(new BigDecimal("1"), tx.amount());
        assertEquals(TransactionStatus.PAID, tx.status());
        assertNull(tx.paidBy(), "PaidBy is null even for a paid test-mode transaction");
        assertEquals(new BigDecimal("1"), tx.products().get(0).price());
    }
}
