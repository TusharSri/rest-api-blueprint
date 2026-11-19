package io.github.tusharsri.blueprint.integration.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Error format and exception mapping seen from the outside, plus trace id propagation. */
@IntegrationTest
class ErrorHandlingApiTest extends AbstractApiIntegrationTest {

    // ---------- the envelope ----------

    @Test
    void every_error_uses_the_same_envelope() throws Exception {
        mvc.perform(auth(get("/api/products/999999"), userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").isNotEmpty())
                .andExpect(jsonPath("$.error.path").value("/api/products/999999"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    @Test
    void successful_responses_do_not_contain_an_error_block() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ---------- status mapping ----------

    @Test
    void an_unknown_path_returns_404() throws Exception {
        mvc.perform(auth(get("/api/does-not-exist"), userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void an_unknown_path_without_a_token_returns_401_not_404() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void an_unsupported_http_method_returns_405_with_an_allow_header() throws Exception {
        mvc.perform(auth(patch("/api/products"), userToken()).contentType(JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void an_unsupported_content_type_returns_415() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(MediaType.APPLICATION_XML).content("<x/>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void malformed_json_returns_400() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void a_wrongly_typed_path_variable_returns_400() throws Exception {
        mvc.perform(auth(get("/api/products/not-a-number"), userToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void an_unexpected_exception_returns_500_without_leaking_details() throws Exception {
        MvcResult result = mvc.perform(get("/api/demo/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andReturn();

        String body = bodyOf(result);
        assertThat((String) read(body, "$.message")).isEqualTo("An unexpected error occurred");
        assertThat(body).doesNotContain("Simulated unexpected failure").doesNotContain("IllegalStateException");
        assertThat(body).doesNotContain("at io.github");
    }

    // ---------- trace id ----------

    @Test
    void every_response_carries_a_generated_trace_id() throws Exception {
        MvcResult result = mvc.perform(auth(get("/api/products/1"), userToken()))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Trace-Id")).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void a_trace_id_sent_by_the_caller_is_echoed_and_appears_in_error_bodies() throws Exception {
        mvc.perform(auth(get("/api/products/999999"), userToken()).header("X-Trace-Id", "client-trace-42"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", "client-trace-42"))
                .andExpect(jsonPath("$.error.traceId").value("client-trace-42"));
    }

    @Test
    void the_error_body_trace_id_matches_the_response_header() throws Exception {
        MvcResult result = mvc.perform(get("/api/products/1")).andExpect(status().isUnauthorized()).andReturn();

        assertThat((String) read(bodyOf(result), "$.error.traceId"))
                .isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void an_unsafe_trace_id_from_the_caller_is_replaced() throws Exception {
        MvcResult result = mvc.perform(auth(get("/api/products/1"), userToken()).header("X-Trace-Id", "bad id with spaces!"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Trace-Id")).isNotEqualTo("bad id with spaces!").matches("[0-9a-fA-F-]{36}");
    }
}
