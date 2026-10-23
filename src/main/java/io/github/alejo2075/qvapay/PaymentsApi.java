package io.github.alejo2075.qvapay;

import io.github.alejo2075.qvapay.auth.AuthPolicy;
import io.github.alejo2075.qvapay.internal.Bodies;
import io.github.alejo2075.qvapay.internal.JsonCodec;
import io.github.alejo2075.qvapay.internal.Operation;
import io.github.alejo2075.qvapay.internal.RequestExecutor;
import io.github.alejo2075.qvapay.model.AuthorizePaymentsRequest;
import io.github.alejo2075.qvapay.model.AuthorizePaymentsResponse;
import io.github.alejo2075.qvapay.model.ChargeRequest;
import io.github.alejo2075.qvapay.model.ChargeResponse;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Direct charges to users who authorized your app ({@code /v2/authorize_payments}, {@code
 * /v2/charge}), authenticated with the app credentials. Obtain it from {@link
 * QvaPayClient#payments()}.
 *
 * <p>Your app needs the {@code allowed_payment_auth} permission, which QvaPay support enables.
 * Nothing here is ever retried automatically.
 */
public final class PaymentsApi {

    private final RequestExecutor executor;

    PaymentsApi(RequestExecutor executor) {
        this.executor = executor;
    }

    /**
     * Requests an authorization URL for a user.
     *
     * <p>Flow: call this, redirect the user to the returned URL, the user signs in to QvaPay and
     * authorizes your app, QvaPay redirects them to your {@code callback}. Only then can you charge
     * that user. The SDK never grants consent on a user's behalf.
     *
     * @param request the user reference and callback
     * @return the response with the URL to open in the user's browser
     * @throws io.github.alejo2075.qvapay.exception.ValidationException if a field is missing or the
     *     app lacks the {@code allowed_payment_auth} permission
     * @throws io.github.alejo2075.qvapay.exception.QvaPayException on any other failure
     */
    public AuthorizePaymentsResponse authorize(AuthorizePaymentsRequest request) {
        return executor.execute(
                authorizeOp(request), b -> JsonCodec.read(b, AuthorizePaymentsResponse.class));
    }

    /**
     * Asynchronous form of {@link #authorize(AuthorizePaymentsRequest)}.
     *
     * @param request the user reference and callback
     * @return a future with the response
     */
    public CompletableFuture<AuthorizePaymentsResponse> authorizeAsync(
            AuthorizePaymentsRequest request) {
        return executor.executeAsync(
                authorizeOp(request), b -> JsonCodec.read(b, AuthorizePaymentsResponse.class));
    }

    private static Operation authorizeOp(AuthorizePaymentsRequest request) {
        Objects.requireNonNull(request, "request");
        return Operation.post(
                "/v2/authorize_payments", Bodies.authorizePayments(request), AuthPolicy.APP, false);
    }

    /**
     * <strong>Charges a user's QvaPay balance immediately. This moves real money.</strong>
     *
     * <p>Requirements: the user must have authorized your app (see {@link
     * #authorize(AuthorizePaymentsRequest)}) and your app needs the {@code allowed_payment_auth}
     * permission. QvaPay deducts a 0.5% processing fee from the payer (GOLD plan users are exempt)
     * and rate limits this endpoint to 5 requests per 20 seconds per app.
     *
     * <p><strong>This call is never retried by the SDK</strong>, not even with a retry policy,
     * because QvaPay documents no idempotency guarantee ({@code remoteId} is your reference, not an
     * idempotency key). After a {@link io.github.alejo2075.qvapay.exception.TransportException}
     * (timeout, connection reset) the charge may or may not have been applied: query {@link
     * TransactionApi#list(int, int)} and look for your {@code remoteId} before charging again.
     *
     * @param request the charge parameters
     * @return the result, including the created transaction
     * @throws io.github.alejo2075.qvapay.exception.ValidationException for invalid data, a missing
     *     permission, a user who has not authorized your app or insufficient balance
     * @throws io.github.alejo2075.qvapay.exception.NotFoundException if the user or app is not
     *     found
     * @throws io.github.alejo2075.qvapay.exception.RateLimitException if the rate limit is exceeded
     * @throws io.github.alejo2075.qvapay.exception.QvaPayException on any other failure
     */
    public ChargeResponse charge(ChargeRequest request) {
        return executor.execute(chargeOp(request), b -> JsonCodec.read(b, ChargeResponse.class));
    }

    /**
     * Asynchronous form of {@link #charge(ChargeRequest)}. <strong>Moves real money</strong> and is
     * never retried; read the warnings on the blocking form.
     *
     * @param request the charge parameters
     * @return a future with the result
     */
    public CompletableFuture<ChargeResponse> chargeAsync(ChargeRequest request) {
        return executor.executeAsync(
                chargeOp(request), b -> JsonCodec.read(b, ChargeResponse.class));
    }

    private static Operation chargeOp(ChargeRequest request) {
        Objects.requireNonNull(request, "request");
        return Operation.post("/v2/charge", Bodies.charge(request), AuthPolicy.APP, false);
    }
}
