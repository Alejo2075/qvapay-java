package io.github.alejo2075.qvapay.exception;

/**
 * Base class of every exception thrown by this SDK.
 *
 * <p>The SDK only throws unchecked exceptions. Messages never contain credentials, request headers
 * or request bodies.
 */
public class QvaPayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public QvaPayException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public QvaPayException(String message, Throwable cause) {
        super(message, cause);
    }
}
