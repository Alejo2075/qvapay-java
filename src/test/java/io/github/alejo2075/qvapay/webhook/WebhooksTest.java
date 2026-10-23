package io.github.alejo2075.qvapay.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhooksTest {

    static final String SECRET = "test-app-secret";
    static final String BODY =
            "{\"transaction_uuid\":\"7c9e6679-7425-40de-944b-e07fc1f90ae7\",\"remote_id\":\"order-12345\",\"status\":\"paid\",\"amount\":\"5.00\",\"signed\":0,\"created_at\":\"2026-10-05T12:00:00.000Z\",\"updated_at\":\"2026-10-05T12:00:30.000Z\"}";

    /** Computed independently with Python's hmac/hashlib. */
    static final String BODY_SIGNATURE =
            "sha256=ce9d67317ff7604a77f1abde9c53d09695772a6661a2f99ba1dbe0295e285c63";

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void signMatchesAnIndependentlyComputedHmac() {
        assertEquals(BODY_SIGNATURE, Webhooks.sign(b(BODY), SECRET));
        assertEquals(
                "sha256=c1ea56948b53132ad57904aac904aa087052a6f88de4d1236961d54b6003c28c",
                Webhooks.sign(b("{}"), SECRET));
    }

    @Test
    void validSignatureIsAccepted() {
        assertTrue(Webhooks.verify(b(BODY), BODY_SIGNATURE, SECRET));
    }

    @Test
    void uppercaseHexIsAcceptedBecauseItDecodesToTheSameBytes() {
        String upper = "sha256=" + BODY_SIGNATURE.substring(7).toUpperCase();
        assertTrue(Webhooks.verify(b(BODY), upper, SECRET));
    }

    @Test
    void wrongSecretIsRejected() {
        assertFalse(Webhooks.verify(b(BODY), BODY_SIGNATURE, "other-secret"));
    }

    @Test
    void tamperedBodyIsRejected() {
        assertFalse(Webhooks.verify(b(BODY.replace("5.00", "500.00")), BODY_SIGNATURE, SECRET));
    }

    @Test
    void reserializedJsonIsRejectedBecauseOnlyTheRawBytesAreSigned() {
        String pretty = BODY.replace(",", ", ");
        assertFalse(Webhooks.verify(b(pretty), BODY_SIGNATURE, SECRET));
        String trailingNewline = BODY + "\n";
        assertFalse(Webhooks.verify(b(trailingNewline), BODY_SIGNATURE, SECRET));
    }

    @Test
    void missingOrMalformedSignaturesAreRejectedWithoutThrowing() {
        byte[] body = b(BODY);
        String hex = BODY_SIGNATURE.substring(7);
        assertFalse(Webhooks.verify(body, null, SECRET));
        assertFalse(Webhooks.verify(body, "", SECRET));
        assertFalse(Webhooks.verify(body, hex, SECRET), "prefix is mandatory");
        assertFalse(Webhooks.verify(body, "sha1=" + hex, SECRET), "wrong algorithm prefix");
        assertFalse(Webhooks.verify(body, "SHA256=" + hex, SECRET), "prefix is case sensitive");
        assertFalse(Webhooks.verify(body, "sha256=" + hex.substring(1), SECRET), "too short");
        assertFalse(Webhooks.verify(body, "sha256=" + hex + "00", SECRET), "too long");
        assertFalse(Webhooks.verify(body, "sha256=" + "z".repeat(64), SECRET), "not hex");
        assertFalse(
                Webhooks.verify(body, "sha256=" + hex.substring(0, 63) + "g", SECRET),
                "one bad char");
        assertFalse(Webhooks.verify(body, "sha256= " + hex.substring(1), SECRET), "whitespace");
        assertFalse(Webhooks.verify(body, " " + BODY_SIGNATURE, SECRET), "leading space");
        assertFalse(Webhooks.verify(body, BODY_SIGNATURE + " ", SECRET), "trailing space");
    }

    @Test
    void emptyBodyHasItsOwnValidSignature() {
        assertTrue(Webhooks.verify(new byte[0], Webhooks.sign(new byte[0], SECRET), SECRET));
    }

    @Test
    void nonAsciiBodiesAreSignedOverTheirUtf8Bytes() {
        byte[] body = b("{\"description\":\"Suscripción ñandú\"}");
        assertTrue(Webhooks.verify(body, Webhooks.sign(body, SECRET), SECRET));
    }

    @Test
    void invalidArgumentsAreProgrammingErrors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Webhooks.verify(null, BODY_SIGNATURE, SECRET));
        assertThrows(
                IllegalArgumentException.class,
                () -> Webhooks.verify(b(BODY), BODY_SIGNATURE, " "));
        assertThrows(
                IllegalArgumentException.class,
                () -> Webhooks.verify(b(BODY), BODY_SIGNATURE, null));
        assertThrows(IllegalArgumentException.class, () -> Webhooks.sign(b(BODY), ""));
    }

    @Test
    void headerNamesAreTheDocumentedOnes() {
        assertEquals("x-qvapay-signature", Webhooks.SIGNATURE_HEADER);
        assertEquals("x-qvapay-timestamp", Webhooks.TIMESTAMP_HEADER);
    }
}
