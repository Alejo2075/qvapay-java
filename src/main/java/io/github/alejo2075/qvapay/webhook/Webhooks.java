package io.github.alejo2075.qvapay.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Low-level signature helpers for QvaPay webhooks.
 *
 * <p>QvaPay signs the <strong>raw request body</strong> with HMAC-SHA256 using your {@code
 * app-secret} as the key and sends {@code x-qvapay-signature: sha256=<hex>}. Always pass the exact
 * bytes you received: parsing and re-serializing the JSON changes them and breaks the signature. In
 * frameworks, read the body as bytes before any JSON binding.
 *
 * <p>A valid signature proves the message came from someone who knows your secret. It does not
 * prevent replays of a genuine message, and the timestamp header is not part of the signed
 * material. Deduplicate by {@code transaction_uuid} and confirm the state through the API before
 * delivering goods (see {@code WebhookVerifier}).
 */
public final class Webhooks {

    /** Name of the signature header. */
    public static final String SIGNATURE_HEADER = "x-qvapay-signature";

    /** Name of the timestamp header (Unix seconds). */
    public static final String TIMESTAMP_HEADER = "x-qvapay-timestamp";

    private static final String PREFIX = "sha256=";
    private static final int HEX_LENGTH = 64;

    private Webhooks() {}

    /**
     * Checks the signature of a webhook body.
     *
     * <p>Returns {@code false} for a missing, malformed or wrong signature; it never throws for bad
     * input from the network. The comparison is constant-time.
     *
     * @param rawBody the exact bytes of the request body
     * @param signatureHeader the value of {@code x-qvapay-signature}, may be {@code null}
     * @param appSecret your application secret
     * @return {@code true} only if the signature matches
     * @throws IllegalArgumentException if {@code rawBody} is null or {@code appSecret} is blank
     */
    public static boolean verify(byte[] rawBody, String signatureHeader, String appSecret) {
        requireInputs(rawBody, appSecret);
        byte[] provided = decodeSignature(signatureHeader);
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(hmac(rawBody, appSecret), provided);
    }

    /**
     * Computes the header value QvaPay would send for a body. Intended for tests and for building
     * signed fixtures; do not use it to "sign" anything else.
     *
     * @param rawBody the body bytes
     * @param appSecret the secret
     * @return {@code sha256=<64 lower-case hex characters>}
     */
    public static String sign(byte[] rawBody, String appSecret) {
        requireInputs(rawBody, appSecret);
        return PREFIX + HexFormat.of().formatHex(hmac(rawBody, appSecret));
    }

    /**
     * Decodes {@code sha256=<64 hex>} into 32 bytes.
     *
     * @param header the header value
     * @return the decoded signature, or {@code null} if absent or malformed
     */
    static byte[] decodeSignature(String header) {
        if (header == null || header.length() != PREFIX.length() + HEX_LENGTH) {
            return null;
        }
        if (!header.startsWith(PREFIX)) {
            return null;
        }
        try {
            return HexFormat.of().parseHex(header.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static byte[] hmac(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    static void requireInputs(byte[] rawBody, String appSecret) {
        if (rawBody == null) {
            throw new IllegalArgumentException("rawBody is required");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("appSecret is required");
        }
    }
}
