package io.github.tusharsri.blueprint.suites;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.SmokeTest;
import io.github.tusharsri.blueprint.support.TestData;
import org.junit.jupiter.api.Test;

/**
 * SMOKE: broad and shallow, runs in seconds. "Is the system alive and are the main doors open?"
 * Run it first, and again after every deployment. If it fails, nothing else is worth running.
 */
@SmokeTest
class SmokeSuiteTest extends AbstractApiIntegrationTest {

    @Test
    void the_application_is_up() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void a_user_can_log_in() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content(TestData.fixture("login-user.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void protected_endpoints_reject_anonymous_callers() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void a_product_can_be_read() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void a_product_can_be_created() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-valid.json")))
                .andExpect(status().isCreated());
    }

    @Test
    void the_api_documentation_is_served() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
