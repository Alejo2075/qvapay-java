package io.github.alejo2075.qvapay.exception;

/**
 * The request could not be completed at the network level: I/O failure, timeout, TLS problem or
 * interruption. When a thread is interrupted its interrupt flag is restored before this exception
 * is thrown.
 *
 * <p>For operations that move money or create resources the outcome is <strong>unknown</strong>:
 * check the state through the API before trying again.
 */
public class TransportException extends QvaPayException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
