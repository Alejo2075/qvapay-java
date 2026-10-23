package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.exception.SerializationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only place that talks to Jackson. The mapper is immutable and thread-safe.
 *
 * <p>Configuration: snake_case property names, unknown properties ignored, {@link BigDecimal}
 * written as plain decimal text (so {@code 25.00} stays {@code 25.00}) and read from either a JSON
 * number or a decimal string.
 */
public final class JsonCodec {

    private static final JsonMapper MAPPER =
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                    .build();

    private JsonCodec() {}

    /**
     * Serializes a value to UTF-8 JSON bytes.
     *
     * @param value a map, record or other bean
     * @return the JSON bytes
     */
    public static byte[] write(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new SerializationException("Could not serialize request body", e);
        }
    }

    /**
     * Parses a JSON document into a type.
     *
     * @param json the raw bytes
     * @param type the target type
     * @param <T> the target type
     * @return the parsed value
     */
    public static <T> T read(byte[] json, Class<T> type) {
        if (json == null || json.length == 0) {
            throw new SerializationException(
                    "Empty response body, expected " + type.getSimpleName(), null);
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new SerializationException(
                    "Could not parse response body as " + type.getSimpleName(), e);
        }
    }

    /**
     * Parses a balance response. QvaPay documents {@code {"balance": 150.75}}, but the live API
     * (verified 2026-10-23, test mode) answers with the bare number {@code 25}. Both forms, and a
     * bare or wrapped decimal string, are accepted. The digits are never rounded.
     *
     * @param json the raw response body
     * @return the balance in USD
     */
    public static java.math.BigDecimal readBalance(byte[] json) {
        String text = json == null ? "" : new String(json, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            throw new SerializationException("Empty response body, expected a balance", null);
        }
        try {
            if (text.startsWith("{")) {
                JsonNode node = MAPPER.readTree(text).get("balance");
                if (node == null || node.isNull()) {
                    throw new SerializationException(
                            "Balance response has no 'balance' field", null);
                }
                return new java.math.BigDecimal(
                        node.isString() ? node.asString().trim() : node.toString());
            }
            if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
                text = text.substring(1, text.length() - 1).trim();
            }
            return new java.math.BigDecimal(text);
        } catch (NumberFormatException | JacksonException e) {
            throw new SerializationException("Could not parse the balance response", e);
        }
    }

    /**
     * Parses a JSON document into a tree.
     *
     * @param json the raw bytes
     * @return the tree, or {@code null} if the input is empty or not valid JSON
     */
    public static JsonNode readTreeOrNull(byte[] json) {
        if (json == null || json.length == 0) {
            return null;
        }
        try {
            return MAPPER.readTree(new String(json, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            return null;
        }
    }
}
