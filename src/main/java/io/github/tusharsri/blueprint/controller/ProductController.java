package io.github.tusharsri.blueprint.controller;

import io.github.tusharsri.blueprint.dto.ApiResult;
import io.github.tusharsri.blueprint.dto.ProductPatchRequest;
import io.github.tusharsri.blueprint.dto.ProductRequest;
import io.github.tusharsri.blueprint.dto.ProductResponse;
import io.github.tusharsri.blueprint.dto.ProductResponseV2;
import io.github.tusharsri.blueprint.idempotency.IdempotencyOutcome;
import io.github.tusharsri.blueprint.idempotency.IdempotencyService;
import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import io.github.tusharsri.blueprint.service.ProductService;
import io.github.tusharsri.blueprint.util.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Products API. GET endpoints exist in two header-selected versions (X-API-Version: 1 or 2);
 * the write endpoints are version-agnostic and answer in the v1 shape.
 */
@RestController
@RequestMapping(ApiConstants.BASE_PATH + "/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService service;
    private final IdempotencyService idempotency;

    public ProductController(ProductService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    // ---------- GET all (query params) ----------
    @GetMapping(version = "1")
    @Operation(summary = "List products (v1)", description = "Query params: page, size, category, sort=field,asc|desc")
    public ApiResult<List<ProductResponse>> getAllV1(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "id,asc") String sort) {
        var result = service.getAll(page, size, category, sort).map(ProductResponse::from);
        return ApiResult.ok("Products fetched", result.content(), ApiResult.Meta.ofPage(result, "1"));
    }

    @GetMapping(version = "2")
    @Operation(summary = "List products (v2: adds sku and stock)")
    public ApiResult<List<ProductResponseV2>> getAllV2(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "id,asc") String sort) {
        var result = service.getAll(page, size, category, sort).map(ProductResponseV2::from);
        return ApiResult.ok("Products fetched", result.content(), ApiResult.Meta.ofPage(result, "2"));
    }

    // ---------- GET one (path variable) ----------
    @GetMapping(value = "/{id}", version = "1")
    @Operation(summary = "Get a product by id (v1)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "404", description = "Product not found")})
    public ApiResult<ProductResponse> getByIdV1(@PathVariable Long id) {
        return ApiResult.ok("Product fetched", ProductResponse.from(service.getById(id)), ApiResult.Meta.ofVersion("1"));
    }

    @GetMapping(value = "/{id}", version = "2")
    @Operation(summary = "Get a product by id (v2: adds sku and stock)")
    public ApiResult<ProductResponseV2> getByIdV2(@PathVariable Long id) {
        return ApiResult.ok("Product fetched", ProductResponseV2.from(service.getById(id)), ApiResult.Meta.ofVersion("2"));
    }

    // ---------- search (query param) ----------
    @GetMapping("/search")
    @Operation(summary = "Search products by name (query param)")
    public ApiResult<List<ProductResponse>> search(@RequestParam @NotBlank String name) {
        List<ProductResponse> found = service.searchByName(name).stream().map(ProductResponse::from).toList();
        return ApiResult.ok("Search completed", found, null);
    }

    // ---------- POST (idempotent via Idempotency-Key header) ----------
    @PostMapping
    @Operation(summary = "Create a product",
            description = "Send an Idempotency-Key header to make retries safe: a repeated key returns the first result "
                    + "with 'Idempotent-Replayed: true'. The same key with a different body returns 422.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created (Location header points to the new resource)"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "SKU already exists"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key reused with a different payload")})
    public ResponseEntity<ApiResult<ProductResponse>> create(
            @Valid @RequestBody ProductRequest request,
            @RequestHeader(name = ApiConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            Principal principal) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return created(service.create(request), false);
        }
        String scopedKey = principal.getName() + ":POST:" + ApiConstants.BASE_PATH + "/products:" + idempotencyKey;
        IdempotencyOutcome<Product> outcome =
                idempotency.execute(scopedKey, request.toString(), () -> service.create(request));
        return switch (outcome) {
            case IdempotencyOutcome.Created<Product> c -> created(c.value(), false);
            case IdempotencyOutcome.Replayed<Product> r -> created(r.value(), true);
        };
    }

    // ---------- PUT (full replace, idempotent) ----------
    @PutMapping("/{id}")
    @Operation(summary = "Replace a product (idempotent)")
    public ApiResult<ProductResponse> replace(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResult.ok("Product replaced", ProductResponse.from(service.replace(id, request)), null);
    }

    // ---------- PATCH (partial update) ----------
    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a product", description = "Only the fields you send are changed.")
    public ApiResult<ProductResponse> patch(@PathVariable Long id, @Valid @RequestBody ProductPatchRequest request) {
        return ApiResult.ok("Product updated", ProductResponse.from(service.patch(id, request)), null);
    }

    // ---------- DELETE (admin only, idempotent) ----------
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a product (ADMIN only, idempotent)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted (or already absent)"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ApiResult<ProductResponse>> created(Product product, boolean replayed) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(product.id()).toUri();
        ResponseEntity.BodyBuilder builder = ResponseEntity.created(location);
        if (replayed) {
            builder.header(ApiConstants.IDEMPOTENT_REPLAYED_HEADER, "true");
        }
        return builder.body(ApiResult.success(201, replayed ? "Product already created (replayed)" : "Product created",
                ProductResponse.from(product), ApiResult.Meta.ofVersion("1")));
    }
}
