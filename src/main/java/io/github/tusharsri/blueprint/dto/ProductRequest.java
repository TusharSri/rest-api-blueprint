package io.github.tusharsri.blueprint.dto;

import io.github.tusharsri.blueprint.model.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Used for POST (create) and PUT (full replace). */
public record ProductRequest(
        @NotBlank @Size(max = 100) @Schema(example = "Mechanical Keyboard") String name,
        @Size(max = 500) @Schema(example = "Hot-swappable, 75% layout") String description,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) @Schema(example = "89.99") BigDecimal price,
        @NotNull Category category,
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "must be 3-20 characters of A-Z, 0-9 or '-'")
        @Schema(example = "KB-75-BLK") String sku,
        @PositiveOrZero Integer stock) {
}
