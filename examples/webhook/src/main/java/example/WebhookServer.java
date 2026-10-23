package example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.alejo2075.qvapay.webhook.InvoiceWebhookEvent;
import io.github.alejo2075.qvapay.webhook.WebhookVerificationException;
import io.github.alejo2075.qvapay.webhook.WebhookVerifier;
import io.github.alejo2075.qvapay.webhook.Webhooks;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Framework-neutral receiver for QvaPay invoice webhooks, built on the JDK HTTP server.
 *
 * <p>Read this as a checklist, not as production code:
 *
 * <ul>
 *   <li>The body is read as <strong>raw bytes</strong> and verified before anything is parsed.
 *   <li>Unsigned, stale or tampered deliveries get {@code 401} and are ignored.
 *   <li>Deliveries are deduplicated by {@code transaction_uuid}. This example keeps the set in
 *       memory; use a database (unique constraint) in a real service.
 *   <li>Answer {@code 2xx} quickly. QvaPay retries on {@code 429}, {@code 5xx} and timeouts (about
 *       0 s, 5 s and 30 s), so persist the event and process it afterwards.
 *   <li>Before delivering goods, compare {@code remote_id}, amount and status with your own order
 *       or confirm with {@code client.transactions().get(uuid)}.
 * </ul>
 *
 * <p>Expose it over HTTPS (a reverse proxy) as QvaPay only delivers to SSL endpoints.
 */
public final class WebhookServer {

    private final HttpServer server;
    private final WebhookVerifier verifier;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final Consumer<InvoiceWebhookEvent> onPaid;

    public WebhookServer(int port, String appSecret, Consumer<InvoiceWebhookEvent> onPaid) throws IOException {
        this.verifier = WebhookVerifier.forSecret(appSecret);
        this.onPaid = onPaid;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.createContext("/qvapay/webhook", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
        int status;
        try {
            if (!"POST".equals(ex.getRequestMethod())) {
                status = 405;
            } else {
                byte[] raw = ex.getRequestBody().readAllBytes(); // raw bytes, never re-serialized
                InvoiceWebhookEvent event = verifier
                        .verifyInvoice(
                                raw,
                                ex.getRequestHeaders().getFirst(Webhooks.SIGNATURE_HEADER),
                                ex.getRequestHeaders().getFirst(Webhooks.TIMESTAMP_HEADER))
                        .event();
                if (seen.add(event.transactionUuid()) && "paid".equals(event.status().wireValue())) {
                    onPaid.accept(event); // in real life: store durably, process asynchronously
                }
                status = 200; // duplicates are acknowledged too, so QvaPay stops retrying
            }
        } catch (WebhookVerificationException e) {
            status = 401; // reason: e.reason(); do not echo details to the caller
        }
        ex.sendResponseHeaders(status, -1);
        ex.close();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        WebhookServer s = new WebhookServer(port, System.getenv("QVAPAY_APP_SECRET"), e ->
                System.out.println("Paid: " + e.remoteId() + " " + e.amount() + " USD (" + e.transactionUuid() + ")"));
        s.start();
        System.out.println("Listening on http://127.0.0.1:" + s.port() + "/qvapay/webhook");
    }
}
