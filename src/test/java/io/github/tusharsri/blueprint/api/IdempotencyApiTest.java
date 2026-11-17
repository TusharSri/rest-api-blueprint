package io.github.tusharsri.blueprint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Idempotency-Key behaviour of POST /api/products. */
class IdempotencyApiTest extends AbstractApiIntegrationTest {

    private static final String KEY = "Idempotency-Key";
    private static final String REPLAYED = "Idempotent-Replayed";

    private MockHttpServletRequestBuilder create(String token, String key, String body) {
        MockHttpServletRequestBuilder request = auth(post("/api/products"), token).contentType(JSON).content(body);
        return key == null ? request : request.header(KEY, key);
    }

    private long totalProducts() throws Exception {
        String body = bodyOf(mvc.perform(auth(get("/api/products").param("size", "1"), userToken())).andReturn());
        return ((Number) read(body, "$.meta.totalElements")).longValue();
    }

    @Test
    void without_a_key_posting_the_same_body_twice_conflicts_because_post_is_not_idempotent_by_itself() throws Exception {
        String body = TestData.product().build();

        mvc.perform(create(userToken(), null, body)).andExpect(status().isCreated());
        mvc.perform(create(userToken(), null, body)).andExpect(status().isConflict());
    }

    @Test
    void the_first_request_with_a_key_creates_without_a_replay_header() throws Exception {
        mvc.perform(create(userToken(), UUID.randomUUID().toString(), TestData.product().build()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(REPLAYED));
    }

    @Test
    void repeating_a_request_with_the_same_key_replays_the_first_result_and_creates_nothing_new() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = TestData.product().build();
        long before = totalProducts();

        MvcResult first = mvc.perform(create(userToken(), key, body)).andExpect(status().isCreated()).andReturn();
        MvcResult replay = mvc.perform(create(userToken(), key, body))
                .andExpect(status().isCreated())
                .andExpect(header().string(REPLAYED, "true"))
                .andReturn();

        assertThat((Object) read(bodyOf(replay), "$.data.id")).isEqualTo(read(bodyOf(first), "$.data.id"));
        assertThat(replay.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(totalProducts()).isEqualTo(before + 1);
    }

    @Test
    void reusing_a_key_with_a_different_body_returns_422() throws Exception {
        String key = UUID.randomUUID().toString();
        String sku = TestData.uniqueSku();
        mvc.perform(create(userToken(), key, TestData.product().sku(sku).price("10.00").build()))
                .andExpect(status().isCreated());

        mvc.perform(create(userToken(), key, TestData.product().sku(sku).price("99.00").build()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void the_same_key_used_by_two_users_is_treated_independently() throws Exception {
        String key = UUID.randomUUID().toString();

        MvcResult user = mvc.perform(create(userToken(), key, TestData.product().build()))
                .andExpect(status().isCreated()).andReturn();
        MvcResult admin = mvc.perform(create(adminToken(), key, TestData.product().build()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(REPLAYED))
                .andReturn();

        assertThat((Object) read(bodyOf(admin), "$.data.id")).isNotEqualTo(read(bodyOf(user), "$.data.id"));
    }

    @Test
    void a_request_that_fails_validation_does_not_burn_the_key() throws Exception {
        String key = UUID.randomUUID().toString();

        mvc.perform(create(userToken(), key, TestData.product().name("").build())).andExpect(status().isBadRequest());
        mvc.perform(create(userToken(), key, TestData.product().build()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(REPLAYED));
    }

    @Test
    void a_request_that_conflicts_does_not_burn_the_key() throws Exception {
        String sku = TestData.uniqueSku();
        createProduct(userToken(), sku);
        String key = UUID.randomUUID().toString();

        mvc.perform(create(userToken(), key, TestData.product().sku(sku).build())).andExpect(status().isConflict());
        mvc.perform(create(userToken(), key, TestData.product().sku(sku).build())).andExpect(status().isConflict());
    }

    @Test
    void a_blank_key_is_treated_as_no_key() throws Exception {
        mvc.perform(create(userToken(), "   ", TestData.product().build()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(REPLAYED));
    }

    @Test
    void parallel_requests_with_the_same_key_create_exactly_one_product() throws Exception {
        int threads = 10;
        String key = UUID.randomUUID().toString();
        String body = TestData.product().build();
        String token = userToken();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                Callable<MvcResult> call = () -> {
                    start.await();
                    return mvc.perform(create(token, key, body)).andReturn();
                };
                futures.add(executor.submit(call));
            }
            start.countDown();
        }

        Set<Object> ids = new HashSet<>();
        int replays = 0;
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get();
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            ids.add(read(bodyOf(result), "$.data.id"));
            if ("true".equals(result.getResponse().getHeader(REPLAYED))) {
                replays++;
            }
        }
        assertThat(ids).hasSize(1);
        assertThat(replays).isEqualTo(threads - 1);
    }
}
