package example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import io.github.alejo2075.qvapay.QvaPayClient;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvoiceDemoTest {

    @Test
    void createsAnInvoiceAgainstAMockServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v2/create_invoice", ex -> {
            byte[] out = ("{\"transaction_uuid\":\"7c9e6679\",\"amount\":25.00,\"url\":\"https://www.qvapay.com/pay/7c9e6679\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        try {
            QvaPayClient client = QvaPayClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .allowInsecureBaseUrlForTesting()
                    .appCredentials("test-app-id", "test-secret-not-real")
                    .build();
            assertEquals("https://www.qvapay.com/pay/7c9e6679", InvoiceDemo.createInvoiceUrl(client));
        } finally {
            server.stop(0);
        }
    }
}
