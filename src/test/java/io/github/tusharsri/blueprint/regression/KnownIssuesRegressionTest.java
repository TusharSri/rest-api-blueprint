package io.github.tusharsri.blueprint.regression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.RegressionTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * REGRESSION pins: each test protects behaviour that once broke or is easy to break again.
 * When a defect is fixed, add a test here named after it, so it can never silently return.
 */
@RegressionTest
class KnownIssuesRegressionTest extends AbstractApiIntegrationTest {

    @Test
    void issue_401_responses_must_carry_a_trace_id_in_header_and_body() throws Exception {
        // Security errors are produced before the controller runs; the trace filter must run even earlier.
        MvcResult result = mvc.perform(get("/api/products/1")).andExpect(status().isUnauthorized()).andReturn();

        assertThat((String) read(bodyOf(result), "$.error.traceId"))
                .isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void issue_unknown_paths_must_not_reveal_existence_to_anonymous_callers() throws Exception {
        mvc.perform(get("/api/definitely-not-a-route")).andExpect(status().isUnauthorized());
    }

    @Test
    void issue_deleting_an_already_deleted_product_must_stay_204() throws Exception {
        long id = createProduct(adminToken());

        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
    }

    @Test
    void issue_put_on_a_missing_product_must_not_create_it() throws Exception {
        mvc.perform(auth(put("/api/products/987654"), userToken()).contentType(JSON).content(TestData.product().build()))
                .andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/products/987654"), userToken())).andExpect(status().isNotFound());
    }

    @Test
    void issue_a_blank_idempotency_key_must_be_ignored_not_stored() throws Exception {
        String body = TestData.product().build();

        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", "  ").contentType(JSON).content(body))
                .andExpect(status().isCreated());
        // same body again: a stored blank key would have replayed (201); without a key it is a duplicate SKU
        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", "  ").contentType(JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void issue_a_rejected_request_must_not_consume_its_idempotency_key() throws Exception {
        String key = UUID.randomUUID().toString();

        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key).contentType(JSON)
                .content(TestData.product().name("").build())).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key).contentType(JSON)
                        .content(TestData.product().build()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void issue_error_responses_must_never_expose_stack_traces_or_exception_names() throws Exception {
        String body = bodyOf(mvc.perform(get("/api/demo/boom")).andExpect(status().isInternalServerError()).andReturn());

        assertThat(body).doesNotContain("Exception").doesNotContain("at io.github").doesNotContain("Simulated unexpected");
    }

    @Test
    void issue_a_cached_product_must_be_evicted_when_it_is_deleted() throws Exception {
        long id = createProduct(adminToken());
        mvc.perform(auth(get("/api/products/" + id), userToken())).andExpect(status().isOk());

        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/products/" + id), userToken())).andExpect(status().isNotFound());
    }
}
