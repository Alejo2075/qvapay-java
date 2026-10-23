package io.github.alejo2075.qvapay.internal;

import io.github.alejo2075.qvapay.model.CreateInvoiceRequest;
import io.github.alejo2075.qvapay.model.InvoiceProduct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts request objects to the exact JSON bodies of the API. Fields that were not set are left
 * out, so the API sees them as absent.
 */
public final class Bodies {

    private Bodies() {}

    /**
     * Body of {@code POST /v2/create_invoice}.
     *
     * @param r the request
     * @return the body fields in documentation order
     */
    public static Map<String, Object> createInvoice(CreateInvoiceRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", r.amount());
        m.put("description", r.description());
        m.put("remote_id", r.remoteId());
        if (r.webhook() != null) {
            m.put("webhook", r.webhook());
        }
        if (!r.products().isEmpty()) {
            m.put("products", products(r.products()));
        }
        if (r.expireAt() != null) {
            m.put("expire_at", Validation.iso(r.expireAt()));
        }
        return m;
    }

    /**
     * Converts product lines.
     *
     * @param products the products
     * @return JSON-ready maps
     */
    public static List<Map<String, Object>> products(List<InvoiceProduct> products) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InvoiceProduct p : products) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("price", p.price());
            if (p.quantity() != null) {
                m.put("quantity", p.quantity());
            }
            out.add(m);
        }
        return out;
    }
}
