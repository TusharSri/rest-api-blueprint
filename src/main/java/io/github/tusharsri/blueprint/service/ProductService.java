package io.github.tusharsri.blueprint.service;

import io.github.tusharsri.blueprint.dto.PageResult;
import io.github.tusharsri.blueprint.dto.ProductPatchRequest;
import io.github.tusharsri.blueprint.dto.ProductRequest;
import io.github.tusharsri.blueprint.exception.BadRequestException;
import io.github.tusharsri.blueprint.exception.ResourceNotFoundException;
import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import io.github.tusharsri.blueprint.repository.ProductRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    static final String CACHE = "products";

    private static final Map<String, Comparator<Product>> SORTERS = Map.of(
            "id", Comparator.comparing(Product::id),
            "name", Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER),
            "price", Comparator.comparing(Product::price),
            "createdAt", Comparator.comparing(Product::createdAt));

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public PageResult<Product> getAll(int page, int size, Category category, String sort) {
        Comparator<Product> comparator = parseSort(sort);
        List<Product> filtered = repository.findAll().stream()
                .filter(p -> category == null || p.category() == category)
                .sorted(comparator)
                .toList();
        long total = filtered.size();
        int totalPages = (int) Math.ceil(total / (double) size);
        List<Product> content = filtered.stream().skip((long) page * size).limit(size).toList();
        return new PageResult<>(content, page, size, total, totalPages);
    }

    @Cacheable(cacheNames = CACHE, key = "#id")
    public Product getById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public List<Product> searchByName(String name) {
        String needle = name.toLowerCase();
        return repository.findAll().stream()
                .filter(p -> p.name().toLowerCase().contains(needle))
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    @CachePut(cacheNames = CACHE, key = "#result.id()")
    public Product create(ProductRequest request) {
        Instant now = Instant.now();
        Product product = new Product(repository.nextId(), request.sku(), request.name().trim(),
                request.description(), request.price(), request.category(),
                request.stock() == null ? 0 : request.stock(), now, now);
        return repository.save(product);
    }

    /** PUT: full replacement. Idempotent: same request, same final state. */
    @CachePut(cacheNames = CACHE, key = "#result.id()")
    public Product replace(Long id, ProductRequest request) {
        Product existing = findOrThrow(id);
        Product updated = new Product(id, request.sku(), request.name().trim(), request.description(),
                request.price(), request.category(), request.stock() == null ? 0 : request.stock(),
                existing.createdAt(), Instant.now());
        return repository.save(updated);
    }

    /** PATCH: partial update; only non-null fields change. */
    @CachePut(cacheNames = CACHE, key = "#result.id()")
    public Product patch(Long id, ProductPatchRequest request) {
        Product existing = findOrThrow(id);
        Product updated = new Product(id, existing.sku(),
                request.name() != null ? request.name().trim() : existing.name(),
                request.description() != null ? request.description() : existing.description(),
                Objects.requireNonNullElse(request.price(), existing.price()),
                Objects.requireNonNullElse(request.category(), existing.category()),
                request.stock() != null ? request.stock() : existing.stock(),
                existing.createdAt(), Instant.now());
        return repository.save(updated);
    }

    /** DELETE: idempotent. Deleting something already gone ends in the same state, so it still succeeds. */
    @CacheEvict(cacheNames = CACHE, key = "#id")
    public void delete(Long id) {
        repository.deleteById(id);
    }

    // not annotated: bypasses the cache on purpose so update logic always sees the stored value
    private Product findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private static Comparator<Product> parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Comparator<Product> comparator = SORTERS.get(field);
        if (comparator == null) {
            throw new BadRequestException("Unsupported sort field '" + field + "'. Allowed: "
                    + SORTERS.keySet().stream().sorted().toList());
        }
        if (parts.length > 1) {
            String direction = parts[1].trim().toLowerCase();
            if (direction.equals("desc")) {
                return comparator.reversed();
            }
            if (!direction.equals("asc")) {
                throw new BadRequestException("Sort direction must be 'asc' or 'desc'");
            }
        }
        return comparator;
    }
}
