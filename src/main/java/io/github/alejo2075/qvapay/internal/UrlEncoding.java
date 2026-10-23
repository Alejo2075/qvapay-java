package io.github.alejo2075.qvapay.internal;

import java.nio.charset.StandardCharsets;

/** Percent-encoding of URL path segments and query values (RFC 3986). */
public final class UrlEncoding {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UrlEncoding() {}

    /**
     * Encodes a value so it is safe as a single path segment or query value. Only unreserved
     * characters ({@code A-Z a-z 0-9 - . _ ~}) are left as they are; everything else, including
     * {@code /}, is percent-encoded as UTF-8.
     *
     * @param value the raw value
     * @return the encoded value
     */
    public static String encode(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '.'
                    || c == '_'
                    || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return sb.toString();
    }
}
