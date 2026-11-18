package io.github.tusharsri.blueprint.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.ContractTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Status codes per endpoint and role, and the headers clients rely on. */
@ContractTest
class HttpSemanticsContractTest extends AbstractApiIntegrationTest {

    /** Access matrix: who gets which status for which endpoint. */
    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @CsvSource({
            "GET,    /api/products,                anonymous, 401",
            "GET,    /api/products,                user,      200",
            "GET,    /api/products/1,              user,      200",
            "GET,    /api/products/999999,         user,      404",
            "GET,    /api/products/search?name=a,  user,      200",
            "POST,   /api/products,                anonymous, 401",
            "PUT,    /api/products/1,              anonymous, 401",
            "PATCH,  /api/products/1,              anonymous, 401",
            "DELETE, /api/products/999999,         anonymous, 401",
            "DELETE, /api/products/999999,         user,      403",
            "DELETE, /api/products/999999,         admin,     204",
            "GET,    /api/auth/login,              anonymous, 405",
            "GET,    /api/demo/status/200,         anonymous, 200",
            "GET,    /api/unknown-endpoint,        anonymous, 401",
            "GET,    /api/unknown-endpoint,        user,      404",
            "GET,    /actuator/health,             anonymous, 200",
            "GET,    /actuator/caches,             anonymous, 401",
            "GET,    /actuator/caches,             user,      403",
            "GET,    /actuator/caches,             admin,     200",
            "GET,    /v3/api-docs,                 anonymous, 200"})
    void access_matrix(String method, String path, String role, int expectedStatus) throws Exception {
        String verb = method.trim();
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(verb), path.trim());
        if (!"anonymous".equals(role.trim())) {
            request = auth(request, "admin".equals(role.trim()) ? adminToken() : userToken());
        }
        if (verb.equals("POST") || verb.equals("PUT") || verb.equals("PATCH")) {
            request = request.contentType(JSON).content("{}");
        }
        MvcResult result = mvc.perform(request).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    void created_responses_carry_a_location_header() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-valid.json")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void every_response_carries_a_trace_id() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken())).andExpect(header().exists("X-Trace-Id"));
        mvc.perform(get("/api/products/1")).andExpect(header().exists("X-Trace-Id"));
        mvc.perform(get("/api/demo/boom")).andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void unauthorized_responses_advertise_the_bearer_scheme() throws Exception {
        mvc.perform(get("/api/products/1")).andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void method_not_allowed_responses_list_the_allowed_methods() throws Exception {
        mvc.perform(auth(patch("/api/products"), userToken()).contentType(JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void idempotent_replays_are_marked_with_a_header() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = TestData.product().build();
        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key).contentType(JSON).content(body))
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key).contentType(JSON).content(body))
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }
}
