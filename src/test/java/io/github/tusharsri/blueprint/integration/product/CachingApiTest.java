package io.github.tusharsri.blueprint.integration.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.IntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Verifies @Cacheable / @CachePut / @CacheEvict behaviour through the real HTTP stack and the real cache. */
@IntegrationTest
class CachingApiTest extends AbstractApiIntegrationTest {

    private static final String CACHE = "products";

    @Autowired
    private CacheManager cacheManager;

    private Cache products() {
        Cache cache = cacheManager.getCache(CACHE);
        assertThat(cache).as("cache '%s' must exist", CACHE).isNotNull();
        return cache;
    }

    @Test
    void creating_a_product_puts_it_in_the_cache() throws Exception {
        long id = createProduct(userToken());

        assertThat(products().get(id, Product.class)).isNotNull();
    }

    @Test
    void reading_a_product_after_eviction_loads_it_into_the_cache_again() throws Exception {
        long id = createProduct(userToken());
        products().evict(id);
        assertThat(products().get(id)).isNull();

        mvc.perform(auth(get("/api/products/" + id), userToken())).andExpect(status().isOk());

        assertThat(products().get(id, Product.class)).isNotNull();
    }

    @Test
    void a_cached_value_is_served_without_touching_the_repository() throws Exception {
        long id = createProduct(userToken());
        Product original = products().get(id, Product.class);
        Product planted = new Product(id, original.sku(), "FROM CACHE", "planted by test", BigDecimal.TEN,
                Category.TOYS, 1, Instant.now(), Instant.now());
        products().put(id, planted);

        try {
            mvc.perform(auth(get("/api/products/" + id), userToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("FROM CACHE"));
        } finally {
            products().evict(id);
        }
    }

    @Test
    void put_refreshes_the_cached_value() throws Exception {
        long id = createProduct(userToken());

        mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON)
                .content(TestData.product().name("Updated via PUT").build())).andExpect(status().isOk());

        assertThat(products().get(id, Product.class).name()).isEqualTo("Updated via PUT");
    }

    @Test
    void patch_refreshes_the_cached_value() throws Exception {
        long id = createProduct(userToken());

        mvc.perform(auth(patch("/api/products/" + id), userToken()).contentType(JSON).content("{\"price\":77.77}"))
                .andExpect(status().isOk());

        assertThat(products().get(id, Product.class).price()).isEqualByComparingTo("77.77");
    }

    @Test
    void delete_evicts_the_cached_value() throws Exception {
        long id = createProduct(adminToken());
        assertThat(products().get(id)).isNotNull();

        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());

        assertThat(products().get(id)).isNull();
    }

    @Test
    void a_missing_product_is_never_cached() throws Exception {
        mvc.perform(auth(get("/api/products/987654"), userToken())).andExpect(status().isNotFound());

        assertThat(products().get(987654L)).isNull();
    }

    @Test
    void a_failed_update_leaves_the_cache_untouched() throws Exception {
        long id = createProduct(userToken());
        String nameBefore = products().get(id, Product.class).name();

        mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON)
                        .content(TestData.product().name("Should not stick").sku("SKU-001").build()))
                .andExpect(status().isConflict());

        assertThat(products().get(id, Product.class).name()).isEqualTo(nameBefore);
    }
}
