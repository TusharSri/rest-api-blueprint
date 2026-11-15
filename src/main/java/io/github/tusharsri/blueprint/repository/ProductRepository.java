package io.github.tusharsri.blueprint.repository;

import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.exception.ConflictException;
import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory store (ConcurrentHashMap + AtomicLong).
 * Reads simulate database latency so the effect of caching is visible in load tests.
 */
@Repository
public class ProductRepository {

    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final long latencyMs;

    public ProductRepository(AppProperties props) {
        this.latencyMs = props.repository().simulatedLatencyMs();
        seed();
    }

    public long nextId() {
        return sequence.incrementAndGet();
    }

    public Optional<Product> findById(Long id) {
        simulateLatency();
        return Optional.ofNullable(store.get(id));
    }

    public List<Product> findAll() {
        simulateLatency();
        return List.copyOf(store.values());
    }

    /** Insert or replace. Rejects a SKU that already belongs to another product. */
    public synchronized Product save(Product product) {
        boolean skuTaken = store.values().stream()
                .anyMatch(p -> p.sku().equals(product.sku()) && !p.id().equals(product.id()));
        if (skuTaken) {
            throw new ConflictException("A product with SKU '" + product.sku() + "' already exists");
        }
        store.put(product.id(), product);
        return product;
    }

    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    private void simulateLatency() {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void seed() {
        Category[] categories = Category.values();
        Instant now = Instant.now();
        for (int i = 1; i <= 30; i++) {
            long id = nextId();
            store.put(id, new Product(id, String.format("SKU-%03d", i), "Sample product " + i,
                    "Seeded demo product number " + i, BigDecimal.valueOf(5L + i * 3L, 0).setScale(2),
                    categories[i % categories.length], 10 + i, now, now));
        }
    }
}
