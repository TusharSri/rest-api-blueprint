package io.github.tusharsri.blueprint.dto;

import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** Response shape for API version 2: adds sku and stock. */
public record ProductResponseV2(Long id, String sku, String name, String description, BigDecimal price,
                                Category category, int stock, Instant createdAt, Instant updatedAt) {
    public static ProductResponseV2 from(Product p) {
        return new ProductResponseV2(p.id(), p.sku(), p.name(), p.description(), p.price(), p.category(),
                p.stock(), p.createdAt(), p.updatedAt());
    }
}
