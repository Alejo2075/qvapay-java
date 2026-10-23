package io.github.alejo2075.qvapay;

import io.github.alejo2075.qvapay.auth.AuthPolicy;
import io.github.alejo2075.qvapay.internal.JsonCodec;
import io.github.alejo2075.qvapay.internal.Operation;
import io.github.alejo2075.qvapay.internal.RequestExecutor;
import io.github.alejo2075.qvapay.internal.UrlEncoding;
import io.github.alejo2075.qvapay.model.AppTransactionDetailResponse;
import io.github.alejo2075.qvapay.model.AppTransactionPageResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Transactions of the app ({@code /v2/transactions}), authenticated with the app credentials.
 * Obtain it from {@link QvaPayClient#transactions()}.
 *
 * <p>These calls are read-only, even though QvaPay exposes them as {@code POST}; they are the only
 * ones in this SDK that an opt-in {@link io.github.alejo2075.qvapay.http.RetryPolicy} may retry.
 */
public final class TransactionApi {

    /** Page size used by QvaPay when none is given. */
    public static final int DEFAULT_TAKE = 30;

    /** Maximum page size allowed by QvaPay. */
    public static final int MAX_TAKE = 100;

    private final RequestExecutor executor;

    TransactionApi(RequestExecutor executor) {
        this.executor = executor;
    }

    /**
     * Lists the first page with the default size of {@value #DEFAULT_TAKE}.
     *
     * @return the transactions, newest first
     */
    public AppTransactionPageResponse list() {
        return list(1, DEFAULT_TAKE);
    }

    /**
     * Lists transactions, newest first.
     *
     * @param page page number, starting at 1
     * @param take page size, from 1 to {@value #MAX_TAKE}
     * @return the transactions of the page
     * @throws IllegalArgumentException if {@code page} or {@code take} is out of range
     */
    public AppTransactionPageResponse list(int page, int take) {
        return executor.execute(
                listOp(page, take), b -> JsonCodec.read(b, AppTransactionPageResponse.class));
    }

    /**
     * Asynchronous form of {@link #list(int, int)}.
     *
     * @param page page number, starting at 1
     * @param take page size, from 1 to {@value #MAX_TAKE}
     * @return a future with the page
     */
    public CompletableFuture<AppTransactionPageResponse> listAsync(int page, int take) {
        try {
            return executor.executeAsync(
                    listOp(page, take), b -> JsonCodec.read(b, AppTransactionPageResponse.class));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Operation listOp(int page, int take) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (take < 1 || take > MAX_TAKE) {
            throw new IllegalArgumentException("take must be between 1 and " + MAX_TAKE);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", page);
        body.put("take", take);
        return Operation.post("/v2/transactions", body, AuthPolicy.APP, true);
    }

    /**
     * Returns one transaction of the app, including the payer once it has been paid.
     *
     * <p>Use it to confirm a payment on your server before delivering goods, in addition to
     * verifying the signed webhook.
     *
     * @param uuid the transaction UUID ({@code transaction_uuid} of the invoice)
     * @return the transaction
     * @throws io.github.alejo2075.qvapay.exception.NotFoundException if it does not exist or does
     *     not belong to the app
     * @throws IllegalArgumentException if {@code uuid} is blank
     */
    public AppTransactionDetailResponse get(String uuid) {
        return executor.execute(
                getOp(uuid), b -> JsonCodec.read(b, AppTransactionDetailResponse.class));
    }

    /**
     * Asynchronous form of {@link #get(String)}.
     *
     * @param uuid the transaction UUID
     * @return a future with the transaction
     */
    public CompletableFuture<AppTransactionDetailResponse> getAsync(String uuid) {
        try {
            return executor.executeAsync(
                    getOp(uuid), b -> JsonCodec.read(b, AppTransactionDetailResponse.class));
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Operation getOp(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid is required");
        }
        return Operation.post(
                "/v2/transactions/" + UrlEncoding.encode(uuid), null, AuthPolicy.APP, true);
    }
}
