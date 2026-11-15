package io.github.tusharsri.blueprint.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Immutable domain object (record). Updates create a new instance. */
public record Product(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Category category,
        int stock,
        Instant createdAt,
        Instant updatedAt) {
}
