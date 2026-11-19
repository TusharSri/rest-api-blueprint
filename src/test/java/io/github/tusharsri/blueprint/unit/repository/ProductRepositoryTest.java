package io.github.tusharsri.blueprint.unit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.exception.ConflictException;
import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import io.github.tusharsri.blueprint.repository.ProductRepository;
import io.github.tusharsri.blueprint.support.UnitTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UnitTest
class ProductRepositoryTest {

    private ProductRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ProductRepository(new AppProperties(null, null, new AppProperties.Repository(0)));
    }

    private static Product product(long id, String sku) {
        Instant now = Instant.now();
        return new Product(id, sku, "Name " + id, "Description", BigDecimal.TEN, Category.TOYS, 1, now, now);
    }

    @Test
    void starts_with_thirty_seeded_products() {
        assertThat(repository.findAll()).hasSize(30);
        assertThat(repository.findById(1L)).isPresent();
        assertThat(repository.findById(30L)).isPresent();
    }

    @Test
    void next_id_is_strictly_increasing() {
        long first = repository.nextId();
        long second = repository.nextId();
        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void saves_and_finds_a_product() {
        Product saved = repository.save(product(repository.nextId(), "REPO-1"));
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void find_by_unknown_id_is_empty() {
        assertThat(repository.findById(12345L)).isEmpty();
    }

    @Test
    void save_with_existing_id_replaces_the_product() {
        Product original = repository.findById(1L).orElseThrow();
        Product changed = new Product(1L, original.sku(), "Changed", original.description(), original.price(),
                original.category(), original.stock(), original.createdAt(), Instant.now());
        repository.save(changed);
        assertThat(repository.findById(1L).orElseThrow().name()).isEqualTo("Changed");
    }

    @Test
    void rejects_a_sku_used_by_another_product() {
        assertThatThrownBy(() -> repository.save(product(repository.nextId(), "SKU-001")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_reports_whether_something_was_removed() {
        assertThat(repository.deleteById(1L)).isTrue();
        assertThat(repository.deleteById(1L)).isFalse();
    }

    @Test
    void concurrent_creates_get_unique_ids_and_lose_no_data() throws Exception {
        int threads = 50;
        List<Future<Product>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                String sku = "CONC-" + i;
                futures.add(executor.submit(() -> repository.save(product(repository.nextId(), sku))));
            }
        }
        List<Long> ids = new ArrayList<>();
        for (Future<Product> future : futures) {
            ids.add(future.get().id());
        }
        assertThat(ids).doesNotHaveDuplicates().hasSize(threads);
        assertThat(repository.findAll()).hasSize(30 + threads);
    }

    @Test
    void concurrent_saves_of_the_same_sku_let_exactly_one_win() throws Exception {
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<Object>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                Callable<Object> task = () -> {
                    start.await();
                    try {
                        repository.save(product(repository.nextId(), "RACE-1"));
                        successes.incrementAndGet();
                    } catch (ConflictException e) {
                        conflicts.incrementAndGet();
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                future.get();
            }
        }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
    }
}
