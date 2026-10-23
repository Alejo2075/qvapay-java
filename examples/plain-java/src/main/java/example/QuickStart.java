package example;

// snippet:imports:start
import io.github.alejo2075.qvapay.QvaPayClient;
import io.github.alejo2075.qvapay.model.AppBalance;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.Invoice;
import java.math.BigDecimal;
// snippet:imports:end

/**
 * Quick start. Run it with the credentials of an app in QvaPay <em>test mode</em>:
 *
 * <pre>
 * export QVAPAY_APP_ID=...
 * export QVAPAY_APP_SECRET=...
 * </pre>
 *
 * The code between the snippet markers is copied verbatim into the README.
 */
public final class QuickStart {

    // snippet:start
    public static void main(String[] args) {
        QvaPayClient client = QvaPayClient.builder()
                .appCredentials(System.getenv("QVAPAY_APP_ID"), System.getenv("QVAPAY_APP_SECRET"))
                .build();

        AppBalance balance = client.merchant().balance();
        System.out.println("Balance: " + balance.balance() + " USD");

        Invoice invoice = client.invoices().create(
                CreateInvoiceRequest.builder()
                        .amount(new BigDecimal("25.00"))
                        .description("Test invoice")
                        .remoteId("order-12345")
                        .build());

        System.out.println("Send the customer to: " + invoice.url());
        System.out.println("Keep this next to your order: " + invoice.transactionUuid());
    }
    // snippet:end

    private QuickStart() {}
}
