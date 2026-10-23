package io.github.alejo2075.qvapay.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.alejo2075.qvapay.exception.SerializationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonCodecTest {

    record Sample(BigDecimal amount, String remoteId, Instant createdAt, Instant expireAt) {}

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsMoneyFromNumberPreservingScale() {
        Sample s = JsonCodec.read(b("{\"amount\": 25.00}"), Sample.class);
        assertEquals(new BigDecimal("25.00"), s.amount());
        assertEquals(2, s.amount().scale());
    }

    @Test
    void readsMoneyFromDecimalString() {
        Sample s = JsonCodec.read(b("{\"amount\": \"5.00\"}"), Sample.class);
        assertEquals(new BigDecimal("5.00"), s.amount());
    }

    @Test
    void doesNotLoseDigitsBeyondDoublePrecision() {
        Sample s = JsonCodec.read(b("{\"amount\": 0.00232558123456789}"), Sample.class);
        assertEquals(new BigDecimal("0.00232558123456789"), s.amount());
    }

    @Test
    void mapsSnakeCaseIsoInstantsAndNull() {
        Sample s =
                JsonCodec.read(
                        b(
                                "{\"remote_id\":\"o-1\",\"created_at\":\"2024-06-20T14:30:00.000Z\","
                                        + "\"expire_at\":null}"),
                        Sample.class);
        assertEquals("o-1", s.remoteId());
        assertEquals(Instant.parse("2024-06-20T14:30:00Z"), s.createdAt());
        assertNull(s.expireAt());
    }

    @Test
    void ignoresUnknownProperties() {
        Sample s = JsonCodec.read(b("{\"remote_id\":\"x\",\"brand_new\":{\"a\":1}}"), Sample.class);
        assertEquals("x", s.remoteId());
    }

    @Test
    void writesBigDecimalAsPlainTextKeepingScale() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", new BigDecimal("25.00"));
        m.put("small", new BigDecimal("1E+2"));
        assertEquals(
                "{\"amount\":25.00,\"small\":100}",
                new String(JsonCodec.write(m), StandardCharsets.UTF_8));
    }

    @Test
    void writesExplicitNullAndOmitsAbsentKeys() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expire_at", null);
        assertEquals(
                "{\"expire_at\":null}", new String(JsonCodec.write(m), StandardCharsets.UTF_8));
    }

    @Test
    void invalidAndEmptyBodiesRaiseSerializationException() {
        assertThrows(
                SerializationException.class, () -> JsonCodec.read(b("not json"), Sample.class));
        assertThrows(SerializationException.class, () -> JsonCodec.read(new byte[0], Sample.class));
    }

    @Test
    void readTreeOrNullIsLenient() {
        assertNull(JsonCodec.readTreeOrNull(b("<html>")));
        assertNull(JsonCodec.readTreeOrNull(null));
        assertEquals(
                "boom",
                JsonCodec.readTreeOrNull(b("{\"error\":\"boom\"}")).path("error").asString());
    }
}
