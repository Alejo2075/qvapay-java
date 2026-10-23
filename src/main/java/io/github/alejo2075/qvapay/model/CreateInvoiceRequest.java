package io.github.alejo2075.qvapay.model;

import io.github.alejo2075.qvapay.internal.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parameters of {@code POST /v2/create_invoice}. Build it with {@link #builder()}.
 *
 * <p>Documented limits are checked when {@link Builder#build()} is called: {@code amount} must be
 * greater than 0 and at most 100000, {@code description} and {@code remoteId} must have 1 to 255
 * characters, {@code webhook} must be an http(s) URL and {@code expireAt} must be in the future.
 * The amount is never rounded.
 */
public final class CreateInvoiceRequest {

    private final BigDecimal amount;
    private final String description;
    private final String remoteId;
    private final String webhook;
    private final List<InvoiceProduct> products;
    private final Instant expireAt;

    private CreateInvoiceRequest(Builder b) {
        this.amount = b.amount;
        this.description = b.description;
        this.remoteId = b.remoteId;
        this.webhook = b.webhook;
        this.products = List.copyOf(b.products);
        this.expireAt = b.expireAt;
    }

    /**
     * Starts a builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the amount to charge in USD.
     *
     * @return the amount
     */
    public BigDecimal amount() {
        return amount;
    }

    /**
     * Returns the payment description.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns your identifier for this payment (not an idempotency key).
     *
     * @return the remote id
     */
    public String remoteId() {
        return remoteId;
    }

    /**
     * Returns the webhook URL for this invoice.
     *
     * @return the URL, or {@code null} to use the app callback
     */
    public String webhook() {
        return webhook;
    }

    /**
     * Returns the product lines.
     *
     * @return an unmodifiable list, empty if none
     */
    public List<InvoiceProduct> products() {
        return products;
    }

    /**
     * Returns the expiration time.
     *
     * @return the instant, or {@code null} for QvaPay's default
     */
    public Instant expireAt() {
        return expireAt;
    }

    @Override
    public String toString() {
        return "CreateInvoiceRequest[amount=" + amount + ", remoteId=" + remoteId + "]";
    }

    /** Builder for {@link CreateInvoiceRequest}. */
    public static final class Builder {

        private BigDecimal amount;
        private String description;
        private String remoteId;
        private String webhook;
        private final List<InvoiceProduct> products = new ArrayList<>();
        private Instant expireAt;

        private Builder() {}

        /**
         * Sets the amount in USD (required).
         *
         * @param amount greater than 0 and at most 100000
         * @return this builder
         */
        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        /**
         * Sets the description (required, 1 to 255 characters).
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets your identifier for this payment (required, 1 to 255 characters).
         *
         * @param remoteId the identifier, e.g. your order number
         * @return this builder
         */
        public Builder remoteId(String remoteId) {
            this.remoteId = remoteId;
            return this;
        }

        /**
         * Sets the webhook URL that receives the payment notification (optional).
         *
         * @param webhook an http(s) URL
         * @return this builder
         */
        public Builder webhook(String webhook) {
            this.webhook = webhook;
            return this;
        }

        /**
         * Adds a product line (optional).
         *
         * @param product the product
         * @return this builder
         */
        public Builder product(InvoiceProduct product) {
            this.products.add(java.util.Objects.requireNonNull(product, "product"));
            return this;
        }

        /**
         * Sets the expiration time (optional).
         *
         * @param expireAt a future instant
         * @return this builder
         */
        public Builder expireAt(Instant expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        /**
         * Validates and builds the request.
         *
         * @return the request
         * @throws IllegalArgumentException if a documented constraint is violated
         */
        public CreateInvoiceRequest build() {
            Validation.amount(amount, "amount");
            Validation.text(description, "description", 255);
            Validation.text(remoteId, "remoteId", 255);
            if (webhook != null) {
                Validation.httpUrl(webhook, "webhook");
            }
            Validation.products(products);
            if (expireAt != null) {
                Validation.future(expireAt, "expireAt");
            }
            return new CreateInvoiceRequest(this);
        }
    }
}
