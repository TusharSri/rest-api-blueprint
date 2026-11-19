package io.github.tusharsri.blueprint.suites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.SanityTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SANITY: narrow and focused. After a change or a hotfix to the product API, does that area still work
 * end to end? A handful of realistic scenarios instead of every edge case.
 */
@SanityTest
class SanitySuiteTest extends AbstractApiIntegrationTest {

    @Test
    void the_full_product_lifecycle_works_end_to_end() throws Exception {
        String sku = TestData.uniqueSku();

        // create
        MvcResult created = mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-valid.json", sku)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        long id = ((Number) read(bodyOf(created), "$.data.id")).longValue();

        // read
        mvc.perform(auth(get("/api/products/" + id), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Fixture product"));

        // replace
        mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-update.json", sku)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Fixture product (updated)"));

        // partial update
        mvc.perform(auth(patch("/api/products/" + id), userToken()).contentType(JSON).content("{\"price\":1.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(1.5));

        // delete (admin) and verify it is gone
        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/products/" + id), userToken())).andExpect(status().isNotFound());
    }

    @Test
    void a_retried_create_is_safe_thanks_to_the_idempotency_key() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = TestData.fixture("product-valid.json");

        MvcResult first = mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key)
                .contentType(JSON).content(body)).andExpect(status().isCreated()).andReturn();
        MvcResult retry = mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key)
                        .contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();

        assertThat((Object) read(bodyOf(retry), "$.data.id")).isEqualTo(read(bodyOf(first), "$.data.id"));
    }

    @Test
    void roles_are_enforced_on_the_product_api() throws Exception {
        mvc.perform(auth(delete("/api/products/1"), userToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void both_api_versions_answer_for_the_same_product() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()).header("X-API-Version", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sku").doesNotExist());
        mvc.perform(auth(get("/api/products/1"), userToken()).header("X-API-Version", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sku").exists());
    }
}
