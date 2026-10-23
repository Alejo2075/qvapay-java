package io.github.alejo2075.qvapay;

import io.github.alejo2075.qvapay.auth.AuthPolicy;
import io.github.alejo2075.qvapay.internal.JsonCodec;
import io.github.alejo2075.qvapay.internal.Operation;
import io.github.alejo2075.qvapay.internal.RequestExecutor;
import io.github.alejo2075.qvapay.model.AppBalance;
import io.github.alejo2075.qvapay.model.AppInfo;
import java.util.concurrent.CompletableFuture;

/**
 * Application level operations of the merchant API ({@code /v2/*}), authenticated with the app
 * credentials. Obtain it from {@link QvaPayClient#merchant()}.
 *
 * <p>Every operation has a blocking form and an {@code ...Async} form returning a {@link
 * CompletableFuture}. Failures are typed exceptions from {@code
 * io.github.alejo2075.qvapay.exception}.
 */
public final class MerchantApi {

    private static final Operation INFO = Operation.post("/v2/info", null, AuthPolicy.APP, true);

    private static final Operation BALANCE =
            Operation.post("/v2/balance", null, AuthPolicy.APP, true);

    private final RequestExecutor executor;

    MerchantApi(RequestExecutor executor) {
        this.executor = executor;
    }

    /**
     * Returns the information of the app (a quick way to verify the credentials).
     *
     * @return the app information
     * @throws io.github.alejo2075.qvapay.exception.AuthenticationException if the credentials are
     *     rejected
     * @throws io.github.alejo2075.qvapay.exception.QvaPayException on any other failure
     */
    public AppInfo info() {
        return executor.execute(INFO, b -> JsonCodec.read(b, AppInfo.class));
    }

    /**
     * Asynchronous form of {@link #info()}.
     *
     * @return a future with the app information
     */
    public CompletableFuture<AppInfo> infoAsync() {
        return executor.executeAsync(INFO, b -> JsonCodec.read(b, AppInfo.class));
    }

    /**
     * Returns the current balance of the app owner, in USD.
     *
     * @return the balance
     * @throws io.github.alejo2075.qvapay.exception.NotFoundException if the owner user is not found
     * @throws io.github.alejo2075.qvapay.exception.QvaPayException on any other failure
     */
    public AppBalance balance() {
        return executor.execute(BALANCE, b -> JsonCodec.read(b, AppBalance.class));
    }

    /**
     * Asynchronous form of {@link #balance()}.
     *
     * @return a future with the balance
     */
    public CompletableFuture<AppBalance> balanceAsync() {
        return executor.executeAsync(BALANCE, b -> JsonCodec.read(b, AppBalance.class));
    }
}
