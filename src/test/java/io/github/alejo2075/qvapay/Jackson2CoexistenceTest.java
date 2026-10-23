package io.github.alejo2075.qvapay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alejo2075.qvapay.model.AppTransaction;
import io.github.alejo2075.qvapay.testsupport.Clients;
import io.github.alejo2075.qvapay.testsupport.FakeTransport;
import io.github.alejo2075.qvapay.testsupport.Fixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Applications often have Jackson 2 (Spring Boot 3, Quarkus...) on the classpath. The SDK uses
 * Jackson 3 (package {@code tools.jackson}), so both must work side by side.
 */
class Jackson2CoexistenceTest {

    @Test
    void sdkWorksWhileJackson2IsOnTheClasspath() throws Exception {
        ObjectMapper jackson2 = new ObjectMapper();
        assertEquals("v", jackson2.readTree("{\"k\":\"v\"}").get("k").asText());

        FakeTransport t =
                FakeTransport.returning(
                        FakeTransport.json(200, Fixtures.load("merchant/transaction-detail.json")));
        AppTransaction tx =
                Clients.withApp(t)
                        .transactions()
                        .get("7c9e6679-7425-40de-944b-e07fc1f90ae7")
                        .transaction();
        assertEquals(new BigDecimal("25.00"), tx.amount());
        assertEquals("Juan", tx.paidBy().name());
    }

    @Test
    void jackson2CanAlsoSerializeSdkRecordsWithoutConflict() throws Exception {
        FakeTransport t = FakeTransport.returning(FakeTransport.json(200, "{\"balance\": 150.75}"));
        var balance = Clients.withApp(t).merchant().balance();
        String json = new ObjectMapper().writeValueAsString(balance);
        assertEquals("{\"balance\":150.75}", json);
    }
}
