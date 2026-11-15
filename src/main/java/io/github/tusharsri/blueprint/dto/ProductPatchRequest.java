package io.github.tusharsri.blueprint.dto;

import io.github.tusharsri.blueprint.model.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Used for PATCH: every field is optional; only non-null fields are applied. */
public record ProductPatchRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 500) String description,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        Category category,
        @PositiveOrZero Integer stock) {
}
