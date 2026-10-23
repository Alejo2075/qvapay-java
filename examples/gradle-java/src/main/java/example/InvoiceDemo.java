package example;

import io.github.alejo2075.qvapay.QvaPayClient;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.Invoice;
import java.math.BigDecimal;

/** Creates an invoice with the given client and returns the payment URL. */
public final class InvoiceDemo {

    private InvoiceDemo() {}

    public static String createInvoiceUrl(QvaPayClient client) {
        Invoice invoice = client.invoices().create(
                CreateInvoiceRequest.builder()
                        .amount(new BigDecimal("25.00"))
                        .description("Test invoice")
                        .remoteId("order-12345")
                        .build());
        return invoice.url();
    }

    public static void main(String[] args) {
        QvaPayClient client = QvaPayClient.builder()
                .appCredentials(System.getenv("QVAPAY_APP_ID"), System.getenv("QVAPAY_APP_SECRET"))
                .build();
        System.out.println(createInvoiceUrl(client));
    }
}
