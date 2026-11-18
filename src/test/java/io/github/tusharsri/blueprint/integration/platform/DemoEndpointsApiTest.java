package io.github.tusharsri.blueprint.integration.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/** Every documented HTTP status code, plus the timeout scenarios. */
@IntegrationTest
class DemoEndpointsApiTest extends AbstractApiIntegrationTest {

    @ParameterizedTest(name = "{0} returns a success envelope")
    @ValueSource(ints = {200, 201, 202})
    void success_statuses_return_the_requested_code(int code) throws Exception {
        mvc.perform(get("/api/demo/status/" + code))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void status_204_has_no_body() throws Exception {
        mvc.perform(get("/api/demo/status/204"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @ParameterizedTest(name = "{0} returns the standard error envelope")
    @ValueSource(ints = {400, 401, 403, 404, 405, 406, 409, 415, 422, 429, 500, 503, 504})
    void error_statuses_use_the_standard_error_envelope(int code) throws Exception {
        mvc.perform(get("/api/demo/status/" + code))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.error.code").isNotEmpty())
                .andExpect(jsonPath("$.error.path").value("/api/demo/status/" + code))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    @Test
    void an_unsupported_demo_code_is_a_400_that_lists_the_supported_ones() throws Exception {
        mvc.perform(get("/api/demo/status/418"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---------- timeouts ----------

    @Test
    void a_fast_upstream_call_completes_with_200() throws Exception {
        MvcResult started = mvc.perform(get("/api/demo/slow").param("delaySeconds", "0"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(5_000);

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("done"));
    }

    @Test
    void a_slow_upstream_call_times_out_with_504() throws Exception {
        MvcResult started = mvc.perform(get("/api/demo/slow").param("delaySeconds", "5"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(10_000); // the 3 s upstream timeout fires well before this

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("GATEWAY_TIMEOUT"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    @ParameterizedTest(name = "delaySeconds={0} is rejected")
    @ValueSource(strings = {"-1", "31", "abc"})
    void the_delay_parameter_is_validated(String delay) throws Exception {
        mvc.perform(get("/api/demo/slow").param("delaySeconds", delay))
                .andExpect(status().isBadRequest());
    }
}
