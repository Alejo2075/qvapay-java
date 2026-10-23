package io.github.alejo2075.qvapay.model;

import java.math.BigDecimal;

/**
 * A product line of an invoice.
 *
 * @param name product name
 * @param price unit price
 * @param quantity quantity, or {@code null} to let QvaPay apply its default of 1
 */
public record InvoiceProduct(String name, BigDecimal price, BigDecimal quantity) {

    /**
     * Creates a product with the default quantity (not sent, QvaPay uses 1).
     *
     * @param name product name
     * @param price unit price
     * @return the product
     */
    public static InvoiceProduct of(String name, BigDecimal price) {
        return new InvoiceProduct(name, price, null);
    }

    /**
     * Creates a product with an explicit quantity.
     *
     * @param name product name
     * @param price unit price
     * @param quantity quantity
     * @return the product
     */
    public static InvoiceProduct of(String name, BigDecimal price, int quantity) {
        return new InvoiceProduct(name, price, BigDecimal.valueOf(quantity));
    }
}
