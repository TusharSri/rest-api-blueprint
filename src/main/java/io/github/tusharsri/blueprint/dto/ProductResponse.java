package io.github.tusharsri.blueprint.dto;

import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** Response shape for API version 1. */
public record ProductResponse(Long id, String name, String description, BigDecimal price,
                              Category category, Instant createdAt, Instant updatedAt) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.id(), p.name(), p.description(), p.price(), p.category(), p.createdAt(), p.updatedAt());
    }
}
