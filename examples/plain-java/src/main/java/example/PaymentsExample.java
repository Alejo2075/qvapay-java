package example;

import io.github.alejo2075.qvapay.QvaPayClient;
import io.github.alejo2075.qvapay.model.AuthorizePaymentsRequest;
import io.github.alejo2075.qvapay.model.ChargeRequest;
import io.github.alejo2075.qvapay.model.ChargeResponse;
import java.math.BigDecimal;

/** Direct charges. These calls move real money outside test mode; never retry them blindly. */
public final class PaymentsExample {

    private PaymentsExample() {}

    // snippet:charge:start
    /** Step 1: get the URL where the user grants your app permission to charge them. */
    static String consentUrl(QvaPayClient client) {
        return client.payments()
                .authorize(new AuthorizePaymentsRequest("user-001", "https://example.com/auth/callback"))
                .url(); // redirect the user's browser here; the SDK never grants consent for them
    }

    /** Step 2 (after the user consented): charge their balance. MOVES MONEY, never retried. */
    static ChargeResponse charge(QvaPayClient client, String userUuid) {
        return client.payments()
                .charge(new ChargeRequest(new BigDecimal("10.00"), userUuid, "Monthly plan", "sub-001"));
        // If this throws TransportException the charge may or may not have happened:
        // look for remote_id "sub-001" in client.transactions().list(...) before charging again.
    }
    // snippet:charge:end
}
