package io.github.alejo2075.qvapay.model;

import io.github.alejo2075.qvapay.internal.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Parameters of {@code POST /v2/modify_invoice}. Build it with {@link #builder(String)}.
 *
 * <p>Only the fields you set are sent; everything else stays unchanged on QvaPay's side. The
 * expiration has three states: not touched (never call {@link Builder#expireAt(Instant)} or {@link
 * Builder#resetExpireAt()}), set to a date ({@code expireAt(instant)}), or reset ({@code
 * resetExpireAt()}), which sends an explicit JSON {@code null} that QvaPay documents as "back to
 * the default of 30 days".
 *
 * <p>Only invoices in {@code pending} state can be modified.
 */
public final class ModifyInvoiceRequest {

    private final String transactionUuid;
    private final BigDecimal amount;
    private final String description;
    private final String remoteId;
    private final String webhook;
    private final List<InvoiceProduct> products;
    private final boolean expireAtSpecified;
    private final Instant expireAt;

    private ModifyInvoiceRequest(Builder b) {
        this.transactionUuid = b.transactionUuid;
        this.amount = b.amount;
        this.description = b.description;
        this.remoteId = b.remoteId;
        this.webhook = b.webhook;
        this.products = b.products == null ? null : List.copyOf(b.products);
        this.expireAtSpecified = b.expireAtSpecified;
        this.expireAt = b.expireAt;
    }

    /**
     * Starts a builder.
     *
     * @param transactionUuid UUID of the invoice transaction to modify
     * @return a new builder
     */
    public static Builder builder(String transactionUuid) {
        return new Builder(transactionUuid);
    }

    /**
     * Returns the UUID of the transaction to modify.
     *
     * @return the transaction UUID
     */
    public String transactionUuid() {
        return transactionUuid;
    }

    /**
     * Returns the new amount.
     *
     * @return the amount, or {@code null} if unchanged
     */
    public BigDecimal amount() {
        return amount;
    }

    /**
     * Returns the new description.
     *
     * @return the description, or {@code null} if unchanged
     */
    public String description() {
        return description;
    }

    /**
     * Returns the new remote id.
     *
     * @return the remote id, or {@code null} if unchanged
     */
    public String remoteId() {
        return remoteId;
    }

    /**
     * Returns the new webhook URL.
     *
     * @return the URL (possibly empty to clear it), or {@code null} if unchanged
     */
    public String webhook() {
        return webhook;
    }

    /**
     * Returns the new product list.
     *
     * @return the products, or {@code null} if unchanged
     */
    public List<InvoiceProduct> products() {
        return products;
    }

    /**
     * Tells whether the expiration is part of this modification.
     *
     * @return {@code true} if {@code expireAt(...)} or {@code resetExpireAt()} was called
     */
    public boolean expireAtSpecified() {
        return expireAtSpecified;
    }

    /**
     * Returns the new expiration. Only meaningful when {@link #expireAtSpecified()} is true.
     *
     * @return the new expiration, or {@code null} meaning "reset to the default"
     */
    public Instant expireAt() {
        return expireAt;
    }

    @Override
    public String toString() {
        return "ModifyInvoiceRequest[transactionUuid=" + transactionUuid + "]";
    }

    /** Builder for {@link ModifyInvoiceRequest}. */
    public static final class Builder {

        private final String transactionUuid;
        private BigDecimal amount;
        private String description;
        private String remoteId;
        private String webhook;
        private List<InvoiceProduct> products;
        private boolean expireAtSpecified;
        private Instant expireAt;

        private Builder(String transactionUuid) {
            this.transactionUuid = transactionUuid;
        }

        /**
         * Changes the amount.
         *
         * @param amount greater than 0 and at most 100000
         * @return this builder
         */
        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * Changes the description.
         *
         * @param description 1 to 255 characters
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Changes the remote id.
         *
         * @param remoteId 1 to 255 characters
         * @return this builder
         */
        public Builder remoteId(String remoteId) {
            this.remoteId = remoteId;
            return this;
        }

        /**
         * Changes the webhook URL.
         *
         * @param webhook an http(s) URL, or an empty string to clear it
         * @return this builder
         */
        public Builder webhook(String webhook) {
            this.webhook = webhook;
            return this;
        }

        /**
         * Replaces the product list.
         *
         * @param products the new products (may be empty)
         * @return this builder
         */
        public Builder products(List<InvoiceProduct> products) {
            this.products = List.copyOf(products);
            return this;
        }

        /**
         * Sets a new expiration time.
         *
         * @param expireAt a future instant
         * @return this builder
         */
        public Builder expireAt(Instant expireAt) {
            this.expireAt = Validation.future(expireAt, "expireAt");
            this.expireAtSpecified = true;
            return this;
        }

        /**
         * Resets the expiration to QvaPay's default (30 days) by sending an explicit {@code null}.
         *
         * @return this builder
         */
        public Builder resetExpireAt() {
            this.expireAt = null;
            this.expireAtSpecified = true;
            return this;
        }

        /**
         * Validates and builds the request.
         *
         * @return the request
         * @throws IllegalArgumentException if a documented constraint is violated
         */
        public ModifyInvoiceRequest build() {
            Validation.text(transactionUuid, "transactionUuid");
            if (amount != null) {
                Validation.amount(amount, "amount");
            }
            if (description != null) {
                Validation.text(description, "description", 255);
            }
            if (remoteId != null) {
                Validation.text(remoteId, "remoteId", 255);
            }
            if (webhook != null && !webhook.isEmpty()) {
                Validation.httpUrl(webhook, "webhook");
            }
            if (products != null) {
                Validation.products(products);
            }
            return new ModifyInvoiceRequest(this);
        }
    }
}
