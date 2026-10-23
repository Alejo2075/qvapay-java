package example;

import io.github.alejo2075.qvapay.QvaPayClient;
import io.github.alejo2075.qvapay.exception.AuthenticationException;
import io.github.alejo2075.qvapay.exception.PermissionDeniedException;
import io.github.alejo2075.qvapay.exception.QvaPayApiException;
import io.github.alejo2075.qvapay.exception.RateLimitException;
import io.github.alejo2075.qvapay.model.AppTransactionDetailResponse;
import io.github.alejo2075.qvapay.model.AppTransactionPageResponse;
import io.github.alejo2075.qvapay.model.TransactionStatus;
import io.github.alejo2075.qvapay.webhook.InvoiceWebhookEvent;
import io.github.alejo2075.qvapay.webhook.WebhookVerifier;
import java.util.concurrent.CompletableFuture;

/** Reading transactions, async calls, error handling and webhook verification. */
public final class AdvancedUsage {

    private AdvancedUsage() {}

    // snippet:transaction:start
    /** Confirms on the server that an invoice was really paid. */
    static boolean isPaid(QvaPayClient client, String transactionUuid) {
        AppTransactionDetailResponse response = client.transactions().get(transactionUuid);
        return response.transaction().status() == TransactionStatus.PAID;
    }
    // snippet:transaction:end

    /** Lists the newest transactions. */
    static AppTransactionPageResponse latest(QvaPayClient client) {
        return client.transactions().list(1, 30);
    }

    // snippet:async:start
    static CompletableFuture<String> balanceText(QvaPayClient client) {
        return client.merchant().balanceAsync()
                .thenApply(b -> "Balance: " + b.balance() + " USD");
    }
    // snippet:async:end

    // snippet:errors:start
    static String describeFailure(QvaPayClient client) {
        try {
            return client.merchant().info().name();
        } catch (AuthenticationException e) {
            return "Wrong app-id/app-secret";
        } catch (PermissionDeniedException e) {
            return "Not allowed: " + e.errorCode(); // e.g. KYC_REQUIRED; branch on the code, not the text
        } catch (RateLimitException e) {
            return "Slow down, retry after " + e.retryAfter().orElse(null);
        } catch (QvaPayApiException e) {
            return "QvaPay answered HTTP " + e.statusCode();
        }
    }
    // snippet:errors:end

    // snippet:webhook:start
    /**
     * Call this from your HTTP endpoint with the raw request body bytes and the two headers.
     * Throws {@code WebhookVerificationException} if the delivery must be rejected (answer 401).
     */
    static InvoiceWebhookEvent handleWebhook(byte[] rawBody, String signature, String timestamp) {
        WebhookVerifier verifier = WebhookVerifier.forSecret(System.getenv("QVAPAY_APP_SECRET"));
        InvoiceWebhookEvent event = verifier.verifyInvoice(rawBody, signature, timestamp).event();
        // Now: deduplicate by event.transactionUuid(), compare amount and remoteId with your order,
        // and only then mark it as paid.
        return event;
    }
    // snippet:webhook:end
}
