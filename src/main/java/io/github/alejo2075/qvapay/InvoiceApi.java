package io.github.alejo2075.qvapay;

import io.github.alejo2075.qvapay.auth.AuthPolicy;
import io.github.alejo2075.qvapay.internal.Bodies;
import io.github.alejo2075.qvapay.internal.JsonCodec;
import io.github.alejo2075.qvapay.internal.Operation;
import io.github.alejo2075.qvapay.internal.RequestExecutor;
import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.Invoice;
import io.github.alejo2075.qvapay.model.ModifyInvoiceRequest;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Payment invoices ({@code /v2/create_invoice}, {@code /v2/modify_invoice}), authenticated with the
 * app credentials. Obtain it from {@link QvaPayClient#invoices()}.
 *
 * <p>Invoice operations create or change resources, so they are <strong>never retried</strong>
 * automatically, even if a retry policy is configured.
 */
public final class InvoiceApi {

    private final RequestExecutor executor;

    InvoiceApi(RequestExecutor executor) {
        this.executor = executor;
    }

    /**
     * Creates a payment invoice in {@code pending} state.
     *
     * <p>Redirect the customer to {@link Invoice#url()} and store {@link Invoice#transactionUuid()}
     * with your order. Payment is confirmed by a signed webhook (see {@code
     * io.github.alejo2075.qvapay.webhook.Webhooks}) or by querying the transaction, never by the
     * browser redirect alone.
     *
     * <p>If this call fails with a transport error the outcome is unknown: the invoice may exist.
     * Look it up through the transaction list using your {@code remoteId} before creating another.
     *
     * @param request the invoice parameters
     * @return the created invoice
     * @throws io.github.alejo2075.qvapay.exception.ValidationException if QvaPay rejects the data
     * @throws io.github.alejo2075.qvapay.exception.QvaPayException on any other failure
     */
    public Invoice create(CreateInvoiceRequest request) {
        return executor.execute(createOp(request), b -> JsonCodec.read(b, Invoice.class));
    }

    /**
     * Asynchronous form of {@link #create(CreateInvoiceRequest)}.
     *
     * @param request the invoice parameters
     * @return a future with the created invoice
     */
    public CompletableFuture<Invoice> createAsync(CreateInvoiceRequest request) {
        return executor.executeAsync(createOp(request), b -> JsonCodec.read(b, Invoice.class));
    }

    private static Operation createOp(CreateInvoiceRequest request) {
        Objects.requireNonNull(request, "request");
        return Operation.post(
                "/v2/create_invoice", Bodies.createInvoice(request), AuthPolicy.APP, false);
    }

    /**
     * Modifies an invoice that is still {@code pending}. Only the fields set on the request are
     * sent; see {@link ModifyInvoiceRequest} for how to clear the expiration.
     *
     * @param request the changes
     * @return the updated invoice
     * @throws io.github.alejo2075.qvapay.exception.ValidationException if the data is invalid or
     *     the transaction is not pending
     * @throws io.github.alejo2075.qvapay.exception.PermissionDeniedException if the transaction
     *     belongs to another app
     * @throws io.github.alejo2075.qvapay.exception.NotFoundException if the transaction does not
     *     exist
     */
    public Invoice modify(ModifyInvoiceRequest request) {
        return executor.execute(modifyOp(request), b -> JsonCodec.read(b, Invoice.class));
    }

    /**
     * Asynchronous form of {@link #modify(ModifyInvoiceRequest)}.
     *
     * @param request the changes
     * @return a future with the updated invoice
     */
    public CompletableFuture<Invoice> modifyAsync(ModifyInvoiceRequest request) {
        return executor.executeAsync(modifyOp(request), b -> JsonCodec.read(b, Invoice.class));
    }

    private static Operation modifyOp(ModifyInvoiceRequest request) {
        Objects.requireNonNull(request, "request");
        return Operation.post(
                "/v2/modify_invoice", Bodies.modifyInvoice(request), AuthPolicy.APP, false);
    }
}
